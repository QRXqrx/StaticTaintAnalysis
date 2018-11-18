package com.wxy.wala.main;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSACFG;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

public class WalaTest {

    private String scopeFile;
    private String exclusion;
    private String mainClass;

    private Logger logger = Logger.getLogger(WalaTest.class.getName());

    public WalaTest() {
        scopeFile = "setting/scope.txt";
        exclusion = "setting/Exclusion.txt";
        mainClass = "Lcom/wxy/wala/test/StaticDataflow";
    }

    private ClassHierarchy cha;
    private AnalysisScope scope;

    public void ReadScope() {
        try {
            scope = AnalysisScopeReader.readJavaScope(scopeFile,
                    (new FileProvider()).getFile(exclusion),
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
                for (IMethod m : c.getAllMethods()) {
                    System.out.println("-- " + m.getSignature());
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

    private IAnalysisCacheView cache;
    private CallGraph cg;
    private CGNode entry;

    // 构建CG图
    public void BuildCG() {
        Iterable<Entrypoint> e = Util.makeMainEntrypoints(scope, cha, mainClass);

        AnalysisOptions o = new AnalysisOptions();
        o.setEntrypoints(e);
        o.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);

        cache = new AnalysisCacheImpl();
        CallGraphBuilder builder = Util.makeZeroOneContainerCFABuilder(o, cache, cha, scope);

        try {
            cg = builder.makeCallGraph(o, null);
        } catch (CallGraphBuilderCancelException e1) {
            e1.printStackTrace();
        }
    }

    public void PrintCG() {
        System.out.println(cg.toString());
    }

    private MethodReference ref;
    private SSACFG cfg;
    private ISSABasicBlock bbk;

    public void SetEntry() {
        for (CGNode i : cg.getEntrypointNodes()) {
            if (i != null) {
                entry = i;
                break;
            }
        } // 获取CG图的入口节点
        ref = entry.getMethod().getReference();
        cfg = entry.getIR().getControlFlowGraph();
        bbk = cfg.entry();
        //DefUse defUse = cache.getDefUse(entry.getIR());

    }

    private Map<MethodReference, HashSet<Integer>> flag = new HashMap<>();

    private void dfs(MethodReference ref, SSACFG cfg, ISSABasicBlock bbk) {
        int ind = cfg.getNumber(bbk);

        // 标记已经遍历的基本块
        // if (flag.get(ref) == null) {
        //     HashSet<Integer> h = new HashSet<>();
        //     h.add(ind);
        //     flag.put(ref, h);
        // } else if (!flag.get(ref).contains(ind)) {
        //     flag.get(ref).add(ind);
        // } else {
        //     return;
        // }

        if (ref.getSignature().startsWith("java")) {
            System.out.println(ref.getSignature());
            return;
        }
        for (SSAInstruction i : ((SSACFG.BasicBlock) bbk).getAllInstructions()) {
            System.out.println(String.format("[%d] %s", i.iindex, i.toString()));
            if (i instanceof SSAInvokeInstruction) {
                SSAInvokeInstruction call = (SSAInvokeInstruction) i;
                //System.out.println(call.getNumberOfReturnValues() + " " + call.getNumberOfPositionalParameters());
                IMethod nmethod = cha.resolveMethod(call.getDeclaredTarget());
                MethodReference nref = nmethod.getReference();
                SSACFG ncfg = cache.getIR(nmethod).getControlFlowGraph();
                //System.out.println(nref.toString());
                dfs(nref, ncfg, ncfg.entry());
            }
        }

        Iterator<ISSABasicBlock> bnext = cfg.getSuccNodes(bbk);
        while (bnext.hasNext()) {
            dfs(ref, cfg, bnext.next());
        }
    }

    public void StartDFS() {
        //System.out.println(ref.toString());
        dfs(ref, cfg, bbk);
    }

    public static void main(String[] args) {
        WalaTest w = new WalaTest();
        w.ReadScope();
        w.BuildCG();
//        w.PrintCG();
        w.SetEntry();
        w.StartDFS();
//        w.PrintInfo();
    }
}
