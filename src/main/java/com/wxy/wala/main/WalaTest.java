package com.wxy.wala.main;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Stack;
import java.util.Vector;

public class WalaTest {

    private String scopeFile;
    private String exclusion;
    private String settingFile;
    private String mainClass;
    private Config config;

    public WalaTest() {
        scopeFile = "setting/scope.txt";
        exclusion = "setting/Exclusion.txt";
        settingFile = "setting/SourceSink.txt";
        config = new Config(settingFile);
        mainClass = config.mainclass;
        // 需要指定程序入口 因为每个类都可能都main函数
    }

    private ClassHierarchy cha;
    private AnalysisScope scope;

    // 读取scopeFile
    public void ReadScope() {
        try {
            File exFile = (new FileProvider()).getFile(exclusion);
            scope = AnalysisScopeReader.readJavaScope(scopeFile,
                    exFile,
                    WalaTest.class.getClassLoader());
            cha = ClassHierarchyFactory.make(scope);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassHierarchyException e) {
            e.printStackTrace();
        }
    }

    // 打印类名以及函数签名
    public void PrintInfo() {
        for (IClass c : cha) {
            if (scope.isApplicationLoader(c.getClassLoader())) {
                System.out.println(c.getName().toString());
                for (IMethod m : c.getDeclaredMethods()) {
                    System.out.println("-- " + m.getSignature());
                    // name是函数名 descriptor是参数和返回值
                }
            }
        }
    }

    // 检查是否有mainClass
    public boolean HasMainClass() {
        boolean flag = true;
        TypeReference ref = TypeReference.findOrCreate(ClassLoaderReference.Application,
                mainClass);
        if (cha.lookupClass(ref) == null) {
            flag = false;
        }
        return flag;
    }

    private CallGraph cg;

    // 构建CG图
    public void BuildCG() {
        Iterable<Entrypoint> e = Util.makeMainEntrypoints(scope, cha, mainClass);

        AnalysisOptions o = new AnalysisOptions();
        o.setEntrypoints(e);
        o.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);

        IAnalysisCacheView cache = new AnalysisCacheImpl();
        CallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(o, cache, cha, scope);

        try {
            cg = builder.makeCallGraph(o, null);
        } catch (CallGraphBuilderCancelException e1) {
            e1.printStackTrace();
        }
    }

    private CGNode entry;

    // 获取CG图的入口节点
    public void SetEntry() {
        for (CGNode i : cg.getEntrypointNodes()) {
            if (i != null) {
                entry = i;
                break;
            }
        }
    }

    // 打印每个CGNode的控制流图信息
    public void PrintCG() {
        for (Iterator<CGNode> it = cg.iterator(); it.hasNext(); ) {
            CGNode n = it.next();
            if (n.getMethod().getSignature().startsWith("com.wxy.wala.test")) {
                System.out.println("-- " + n.getMethod().getSignature());
                System.out.println(n.getIR().getControlFlowGraph());
            }
        }
    }

    private CGNode findMethod(String sig) {
        for (Iterator<CGNode> it = cg.iterator(); it.hasNext(); ) {
            CGNode n = it.next();
            if (n.getMethod().getSignature().equals(sig)) {
                return n;
            }
        }
        return null;
    }

    private void DFS(Stack<CGNode> stnode, Stack<ISSABasicBlock> stbbk, InfoStack istk) {
        ISSABasicBlock bbk = stbbk.peek();
        // 获取当前基本块
        Info cursdata = istk.Top();
        // 获取当前污染信息

        for (SSAInstruction i : ((SSACFG.BasicBlock) bbk).getAllInstructions()) {

            System.out.println(String.format("<%d> %s", i.iindex, i.toString()));

            if (i instanceof SSAInvokeInstruction) {
                SSAInvokeInstruction call = (SSAInvokeInstruction) i;
                String sig = call.getDeclaredTarget().getSignature();
                //System.out.println("----"+sig+"----");
                // 函数调用时 返回值是唯一的 也就是getReturnValue(0) 如果为-1则没有返回值

                // 处理系统函数
                if (config.isSource(sig)) {
                    System.out.println("[SOURCE] FIND SOURCE FUNCTION");
                    cursdata.hdsource(call.getReturnValue(0)); // source点返回值总不能是-1吧
                    continue;
                } else if (sig.startsWith("java")) {
                    if (config.isSink(sig)) {
                        System.out.println("[SINK] FIND SINK FUNCTION");
                        for (int k = 0; k < call.getNumberOfUses(); k++) {
                            if (cursdata.reg.contains(call.getUse(k))) {
                                System.out.printf("!!!!![ALERT] FIND A PATH FROM SOURCE TO SINK [arg:%d]!!!!!\n", call.getUse(k));
                            }
                        }
                    }

                    Vector<Integer> tpv = new Vector<>();
                    for (int k = 0; k < call.getNumberOfUses(); k++) tpv.add(call.getUse(k));
                    tpv.add(call.getReturnValue(0));

                    cursdata.ckspr(tpv);
                    continue;
                }

                // 其他情况则需要进入函数内部
                Info newdata = new Info();
                for (int k = 0; k < call.getNumberOfUses(); k++) newdata.ori.add(call.getUse(k));
                newdata.ori.add(call.getReturnValue(0));
                // 把调入参数和返回值的寄存器压入

                newdata.expand(cursdata);
                // 根据已有数据为栈中加入污染信息
                //cursdata.PrintInfo();
                istk.Push(newdata);

                CGNode nnode = findMethod(call.getDeclaredTarget().getSignature()); // 分叉处进行clone
                ISSABasicBlock nbbk = nnode.getIR().getControlFlowGraph().entry();
                stnode.push(nnode);
                stbbk.push(nbbk);
                DFS(stnode, stbbk, istk);
                return;
            } else if (i instanceof SSAReturnInstruction) {
                SSAReturnInstruction ret = (SSAReturnInstruction) i;
                if (istk.IsLast()) {
                    System.out.println("[END] finish traverse one path");
                    System.out.println();
                    return;
                }
                cursdata.writeret(ret.getUse(0));
                // -1 表示没有返回值

                //cursdata.PrintInfo();
                istk.Pop();
                Info cdata = istk.Top();
                cdata.combine(cursdata);
                // 根据映射关系把data中的污染信息记录到curdata中
                stnode.pop();
                stbbk.pop();
                if (!stnode.empty()) {
                    // 此时应该跳转到上一层
                    nextStep(stnode, stbbk, istk);
                }
                return;
                //System.out.println(ret.getUse(0)+" "+bbk.getMethod().getDeclaringClass());
            } else if (i instanceof SSAPutInstruction) {
                SSAPutInstruction iput = (SSAPutInstruction) i;
                IField iField = cha.resolveField(iput.getDeclaredField());
                if (cursdata.reg.contains(iput.getUse(1))) {
                    System.out.printf("[PUT OP] from v%d to field %s in v%d\n", iput.getUse(1), iField.toString(), iput.getUse(0));
                    cursdata.addField(iput.getUse(0), iField);
                }
                // 0是当前对象 1是提供复制的寄存器
            } else if (i instanceof SSAGetInstruction) {
                SSAGetInstruction iget = (SSAGetInstruction) i;
                IField iField = cha.resolveField(iget.getDeclaredField());
                if (cursdata.checkField(iget.getUse(0), iField)) {
                    cursdata.reg.add(iget.getDef(0));
                    System.out.printf("[GET] from field %s in v%d to v%d\n", iField.toString(), iget.getUse(0), iget.getDef(0));
                }
                // def(0)是赋值对象 use(0)是field来源的寄存器
            } else if (i instanceof SSABinaryOpInstruction) {
                SSABinaryOpInstruction iop = (SSABinaryOpInstruction) i;
                System.out.println("OP " + iop.getDef() + " " + iop.getUse(0) + " " + iop.getUse(1));
            } // 还要处理phi
        }
        nextStep(stnode, stbbk, istk);
    }

    private void nextStep(Stack<CGNode> stnode, Stack<ISSABasicBlock> stbbk, InfoStack istk) {
        CGNode node = stnode.peek();
        ISSABasicBlock bbk = stbbk.peek();
        Iterator<ISSABasicBlock> bnext = node.getIR().getControlFlowGraph().getSuccNodes(bbk);

        while (bnext.hasNext()) { // 可能发生分叉 所以栈要复制
            Stack<CGNode> nstnode = (Stack<CGNode>) stnode.clone();
            Stack<ISSABasicBlock> nstbbk = (Stack<ISSABasicBlock>) stbbk.clone();
            nstbbk.pop();
            nstbbk.push(bnext.next());
            DFS(nstnode, nstbbk, istk.Copy());
        }
    }

    public void StartDFS() {
        // System.out.println(entry.getIR().getControlFlowGraph());
        Stack<CGNode> stnode = new Stack<>();
        Stack<ISSABasicBlock> stbbk = new Stack<>();
        stnode.push(entry);
        stbbk.push(entry.getIR().getControlFlowGraph().entry());

        InfoStack istk = new InfoStack();
        istk.Init();
        DFS(stnode, stbbk, istk);
    }

    public static void main(String[] args) {
        WalaTest w = new WalaTest();
        w.ReadScope();
        w.BuildCG();
        //w.PrintCG();
        w.SetEntry();
        w.StartDFS();
        //w.PrintInfo();
    }
}
