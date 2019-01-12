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

public class WalaTest {

    private String scopeFile;
    private String exclusion;
    private String settingFile;
    private String mainClass; // 需要指定程序入口 因为每个类都可能都main函数
    private Config config;
    //private Logger logger = Logger.getLogger(WalaTest.class.getName());

    public WalaTest() {
        scopeFile = "setting/scope.txt";
        exclusion = "setting/Exclusion.txt";
        settingFile = "setting/SourceSink.txt";
        mainClass = "Lcom/wxy/wala/test/Main";
        config = new Config(settingFile);
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

    public void SetEntry() {
        for (CGNode i : cg.getEntrypointNodes()) {
            if (i != null) {
                entry = i;
                break;
            }
        } // 获取CG图的入口节点
        // ref = entry.getMethod().getReference();
        //cfg = entry.getIR().getControlFlowGraph();
        //bbk = cfg.entry();
        //DefUse defUse = cache.getDefUse(entry.getIR());

    }

    //private Map<MethodReference, HashSet<Integer>> flag = new HashMap<>();

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

    private void DFS(Stack<CGNode> stnode, Stack<ISSABasicBlock> stbbk) {
        CGNode node = stnode.peek();
        ISSABasicBlock bbk = stbbk.peek();
        // 获取当前node和basicblock

        String sig = node.getMethod().getSignature();
        if (sig.startsWith("java")) {
            //System.out.println("[Standard library] "+sig);
            stnode.pop();
            stbbk.pop();
            nextStep(stnode, stbbk);
            return; // 不进入系统函数
            // } else {
            //     System.out.println(sig);
        }

        for (SSAInstruction i : ((SSACFG.BasicBlock) bbk).getAllInstructions()) {
            System.out.println(String.format("[%d] %s", i.iindex, i.toString()));
            if (i instanceof SSAInvokeInstruction) {
                /*
                函数调用时 返回值是唯一的 也就是getReturnValue(0) 如果为-1则没有返回值

                 */
                SSAInvokeInstruction call = (SSAInvokeInstruction) i;

                System.out.println("CALL " +
                        call.getCallSite().getDeclaredTarget().getSignature() + " " +
                        call.getNumberOfUses() + " " +
                        call.getReturnValue(0));
                // getuse获取参数
                // 在这里检查是否是库函数 和source sink点一块检查

                // 其他情况则需要进入函数内部
                CGNode nnode = findMethod(call.getDeclaredTarget().getSignature()); // 分叉处进行clone
                ISSABasicBlock nbbk = nnode.getIR().getControlFlowGraph().entry();
                stnode.push(nnode);
                stbbk.push(nbbk);
                DFS(stnode, stbbk);
                return;
            } else if (i instanceof SSAReturnInstruction) {
                SSAReturnInstruction ret = (SSAReturnInstruction) i;

                System.out.println("RETURN " + ret.getUse(0));
                // -1 表示没有返回值
                stnode.pop();
                stbbk.pop();
                if (!stnode.empty()) {
                    // 此时应该跳转到上一层
                    nextStep(stnode, stbbk);
                }
                return;
                //System.out.println(ret.getUse(0)+" "+bbk.getMethod().getDeclaringClass());
            } else if (i instanceof SSAPutInstruction) {
                SSAPutInstruction iput = (SSAPutInstruction) i;
                IField iField = cha.resolveField(iput.getDeclaredField());
                System.out.println("PUT " + iField + " " + iput.getUse(0) + " " + iput.getUse(1));
                // 0可能是对象的变化 1是赋值的内容（过程内）
            } else if (i instanceof SSAGetInstruction) {
                SSAGetInstruction iget = (SSAGetInstruction) i;
                IField iField = cha.resolveField(iget.getDeclaredField());
                System.out.println("GET " + iField + " ");
            } else if (i instanceof SSABinaryOpInstruction) {
                SSABinaryOpInstruction iop = (SSABinaryOpInstruction) i;
                System.out.println("OP " + iop.getDef() + " " + iop.getUse(0) + " " + iop.getUse(1));
            } // 还要处理phi arrayload应该没有必要
        }
        nextStep(stnode, stbbk);
    }

    private void nextStep(Stack<CGNode> stnode, Stack<ISSABasicBlock> stbbk) {
        CGNode node = stnode.peek();
        ISSABasicBlock bbk = stbbk.peek();
        Iterator<ISSABasicBlock> bnext = node.getIR().getControlFlowGraph().getSuccNodes(bbk);

        while (bnext.hasNext()) { // 可能发生分叉 所以栈要复制
            Stack<CGNode> nstnode = (Stack<CGNode>) stnode.clone();
            Stack<ISSABasicBlock> nstbbk = (Stack<ISSABasicBlock>) stbbk.clone();
            nstbbk.pop();
            nstbbk.push(bnext.next());
            DFS(nstnode, nstbbk);
        }
    }

    // private void dfs(MethodReference ref, SSACFG cfg, ISSABasicBlock bbk) {
    //     int ind = cfg.getNumber(bbk);
    //
    //     // 标记已经遍历的基本块
    //     // if (flag.get(ref) == null) {
    //     //     HashSet<Integer> h = new HashSet<>();
    //     //     h.add(ind);
    //     //     flag.put(ref, h);
    //     // } else if (!flag.get(ref).contains(ind)) {
    //     //     flag.get(ref).add(ind);
    //     // } else {
    //     //     return;
    //     // }
    //     System.out.println("* " + ref.getSignature());
    //
    //     if (ref.getSignature().startsWith("java")) {
    //         System.out.println("- Skip standard library function");
    //         return;
    //     }
    //
    //     for (SSAInstruction i : ((SSACFG.BasicBlock) bbk).getAllInstructions()) {
    //         System.out.println(String.format("[%d] %s", i.iindex, i.toString()));
    //         if (i instanceof SSAInvokeInstruction) {
    //             SSAInvokeInstruction call = (SSAInvokeInstruction) i;
    //
    //             int rets = call.getNumberOfReturnValues();
    //             int pars = call.getNumberOfPositionalParameters();
    //
    //             if (rets == 1) System.out.println("return register:" + call.getDef(0));
    //             // for(int k=0;k<pars;k++){
    //             //     Sy
    //             // }
    //
    //             IMethod nmethod = cha.resolveMethod(call.getDeclaredTarget());
    //             MethodReference nref = nmethod.getReference();
    //             SSACFG ncfg = cache.getIR(nmethod).getControlFlowGraph();
    //             //System.out.println(nref.toString());
    //             dfs(nref, ncfg, ncfg.entry());
    //         } else if (i instanceof SSAGetInstruction) {
    //             SSAGetInstruction get = (SSAGetInstruction) i;
    //
    //
    //         }
    //     }
    //
    //     Iterator<ISSABasicBlock> bnext = cfg.getSuccNodes(bbk);
    //     while (bnext.hasNext()) {
    //         // 函数内是路径敏感的 不大对啊
    //         dfs(ref, cfg, bnext.next());
    //     }
    // }

    public void StartDFS() {
        // 使用栈才存储函数调用正确的返回路径
        // System.out.println(entry.getIR().getControlFlowGraph());
        Stack<CGNode> stnode = new Stack<>();
        Stack<ISSABasicBlock> stbbk = new Stack<>();
        stnode.push(entry);
        stbbk.push(entry.getIR().getControlFlowGraph().entry());
        DFS(stnode, stbbk);
    }

    public static void main(String[] args) {
        WalaTest w = new WalaTest();
        //w.ReadConfig();
        //w.ReadScope();
        //w.BuildCG();
        //w.PrintCG();
        //w.SetEntry();
        //w.StartDFS();
        //w.PrintInfo();
    }
}
