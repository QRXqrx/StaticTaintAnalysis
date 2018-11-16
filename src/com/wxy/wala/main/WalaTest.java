package com.wxy.wala.main;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
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

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

public class WalaTest {

    public String scopeFile;
    public String exclusion;
    public String mainClass;

    private Logger logger = Logger.getLogger(WalaTest.class.getName());
    private ClassHierarchy cha;
    private AnalysisScope scope;

    public WalaTest() {
        scopeFile = "setting/myscope";
        exclusion = "setting/Exclusion.txt";
        mainClass = "Lcom/wxy/wala/test/StaticDataflow";
    }

    public void ReadScope() {
        try {
            File exFile = new FileProvider().getFile(exclusion);
            scope = AnalysisScopeReader.readJavaScope(scopeFile,
                    exFile,
                    WalaTest.class.getClassLoader());
            cha = ClassHierarchyFactory.make(scope); // *解析类名，方法名等
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassHierarchyException e) {
            e.printStackTrace();
        }
    }

    public boolean HasMainClass() {
        boolean flag = true;
        TypeReference ref = TypeReference.findOrCreate(ClassLoaderReference.Application,
                mainClass);
        if (cha.lookupClass(ref) == null) {
            flag = false;
        }
        return flag;
    }

    public void PrintInfo() {
        for (IClass klass : cha) {
            if (scope.isApplicationLoader(klass.getClassLoader())) {  // *application记录在scope文件中
                System.out.println(klass.toString());
                for (IMethod method : klass.getAllMethods()) {
                    System.out.println("-- " + method.getSignature());
                }
            }
        }
    }

    private IAnalysisCacheView cache;
    private CallGraph cg;
    private CGNode entry;

    public void BuildCG() {
        Iterable<Entrypoint> e = Util.makeMainEntrypoints(scope, cha, mainClass);
        AnalysisOptions o = new AnalysisOptions(scope, e);
        o.setReflectionOptions(AnalysisOptions.ReflectionOptions.NONE);

        cache = new AnalysisCacheImpl();
        CallGraphBuilder builder = Util.makeZeroOneCFABuilder(Language.JAVA, o, cache, cha, scope);

        try {
            cg = builder.makeCallGraph(o, null);
        } catch (CallGraphBuilderCancelException e1) {
            e1.printStackTrace();
        }
    }

    private SSACFG cfg;
    private MethodReference mref;
    private ISSABasicBlock basicBlock;

    public void SetEntry() {
        for (CGNode i : cg.getEntrypointNodes()) {
            if (i != null) {
                entry = i;
                break;
            }
        } // *获得cg图的根节点
        cfg = entry.getIR().getControlFlowGraph();
        mref = entry.getMethod().getReference();
        basicBlock = cfg.entry();
        //DefUse defUse = cache.getDefUse(entry.getIR());
    }

    private Map<MethodReference, HashSet<Integer>> flag = new HashMap<>();

    private void dfs(SSACFG cfg, MethodReference ref, ISSABasicBlock bbk) {
        // 判断是否访问过
        // visit
        int ind = cfg.getNumber(bbk);
        if (flag.get(ref) == null) {
            HashSet<Integer> h = new HashSet<Integer>();
            h.add(ind);
            flag.put(ref, h);
        } else if (!flag.get(ref).contains(ind)) {
            flag.get(ref).add(ind);
        } else {
            return;
        }

        for (SSAInstruction i : ((SSACFG.BasicBlock) bbk).getAllInstructions()) {
            System.out.println(i.iindex + " " + i.toString());
            if (i instanceof SSAInvokeInstruction) {
                SSAInvokeInstruction cal = (SSAInvokeInstruction) i;
                IMethod mnext = cha.resolveMethod(cal.getDeclaredTarget());
                MethodReference nref = mnext.getReference();
                SSACFG ncfg = cache.getIR(mnext).getControlFlowGraph();
                System.out.println(mref.toString());
                dfs(ncfg, nref, ncfg.entry());
            }
        }

        Iterator<ISSABasicBlock> bnext = cfg.getSuccNodes(bbk);

        while (bnext.hasNext()) {
            dfs(cfg, ref, bnext.next());
        }
    }

    public void StartDFS() {
        System.out.println(mref.toString());
        dfs(cfg, mref, basicBlock);
    }

    public static void main(String[] args) {
        WalaTest w = new WalaTest();
        w.ReadScope();
        w.BuildCG();
        w.SetEntry();
        w.StartDFS();
        //w.PrintInfo();
    }
}
