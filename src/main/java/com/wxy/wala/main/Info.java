package com.wxy.wala.main;

import com.ibm.wala.classLoader.IField;

import java.util.HashSet;
import java.util.Vector;

public class Info {

    public Info Copy() {
        Info ninf = new Info();
        ninf.reg.addAll(reg);
        ninf.fld.addAll(fld);
        ninf.ori.addAll(ori);
        ninf.cur.addAll(cur);
        // for (Integer i : reg) ninf.reg.add(i);
        // for (SField sf : fld) ninf.fld.add(sf);
        // for (Integer i : ori) ninf.ori.add(i);
        // for (Integer i : cur) ninf.cur.add(i);
        return ninf;
    }

    public Info() {
    }

    // 需要记录field关联哪个寄存器（类的某个实例）
    class SField {
        public int reg;
        public IField field;

        public SField(int r, IField f) {
            reg = r;
            field = f;
        }

        public int hashCode() {
            Integer tp = new Integer(reg);
            return tp.hashCode() ^ field.hashCode();
        }

        public boolean equals(Object o) {
            if (reg == ((SField) o).reg && field.toString().equals(((SField) o).field.toString())) {
                return true;
            } else {
                return false;
            }
        }


    }

    // 被污染的寄存器/field
    public HashSet<Integer> reg = new HashSet<>();
    public HashSet<SField> fld = new HashSet<>();

    // 相当于一个映射表，映射caller和callee之间寄存器的关系
    // ori(v4,v6,v10) -> cur(v1,v2,v8) 最后一个是返回值
    public Vector<Integer> ori = new Vector<>();
    public Vector<Integer> cur = new Vector<>();

    // 暂时忽略static

    // 由新生成的Info调用，传入参数被污染的情况，info是上一层的污染信息
    // 事先需要填充ori相关信息
    public void expand(Info info) {
        if (ori.size() == 1) return;// 没有传入参数

        for (int i = 1; i < ori.size(); i++) {
            cur.add(i);
        } // 传入的第一个参数对应v1，第二个参数对应v2，以此类推

        for (int i = 0; i < ori.size() - 1; i++) {
            if (info.reg.contains(ori.get(i))) {
                reg.add(i + 1);
                System.out.printf("[EXPAND] v%d->v%d is polluted\n", ori.get(i), i + 1);
            }
        } // 检查传入的参数是否是被污染的，例如上一层v6被污染，则这一层v2也是被污染的
        // 可以合并，但没有必要

        int curobj = ori.firstElement(); // 可能是调用者对应的对象
        for (SField f : info.fld) { // 这个只能遍历查找了
            if (curobj == f.reg) {
                SField nf = new SField(1, f.field);
                fld.add(nf);
                System.out.printf("[EXPAND] field:%s is polluted\n", f.field.toString());
                //不能break，因为可能对多个field被污染
            }
        }
    }

    // 合并函数调用后获得的污染信息，由下层的Info调用
    public void combine(Info info) {
        if (info.reg.contains(info.cur.lastElement())) {
            reg.add(info.ori.lastElement());
            System.out.printf("[COMBINE] return value v%d->v%d is polluted\n", info.cur.lastElement(), info.ori.lastElement());
        } // 判断返回值是否被污染
        int curobj = info.ori.firstElement();
        for (SField sf : info.fld) {// 由SField实现自动去重
            SField nf = new SField(curobj, sf.field);
            fld.add(nf);
            System.out.printf("[COMBINE] field:%s in v%d is polluted\n", sf.field, curobj);
        }
    }

    public void addField(int i, IField f) {
        SField nsf = new SField(i, f);
        fld.add(nsf);
    }

    public boolean checkField(int i, IField f) {
        for (SField sf : fld) {
            if (sf.reg == i && sf.field.toString().equals(f.toString())) {
                return true;
            }
        }
        return false;
    }

    public void writeret(int i) {
        cur.add(i);
    }

    // 如果发现是source函数，直接将函数的返回值标记为被污染
    public void hdsource(int retreg) {
        System.out.printf("[SOURSE] v%d is polluted from source\n", retreg);
        reg.add(retreg);
    }

    // 其它系统函数检查参数是否被污染，如果被污染使用强制传播规则
    public void ckspr(Vector<Integer> varg) {
        if (varg.size() == 1) return;

        for (int i = 0; i < varg.size() - 1; i++) {
            if (reg.contains(varg.get(i))) {
                int first = varg.firstElement();
                int end = varg.lastElement();
                reg.add(first);
                reg.add(end);
                System.out.printf("[SPREAD] spread from v%d to v%d and v%d\n", varg.get(i), first, end);
                break;
            }
        }
    }

    public void PrintInfo() {
        System.out.printf("传入寄存器：");
        for (Integer i : ori) {
            System.out.printf("%d,", i);
        }
        System.out.println();
        System.out.printf("当前寄存器：");
        for (Integer i : cur) {
            System.out.printf("%d,", i);
        }
        System.out.println();
        System.out.printf("污染寄存器：");
        for (Integer i : reg) {
            System.out.printf("%d,", i);
        }
        System.out.println();
        System.out.printf("污染Field：");
        for (SField sf : fld) {
            System.out.printf("(%d,%s),", sf.reg, sf.field);
        }
        System.out.println();
    }
}
