package com.wxy.wala.main;

import java.util.Vector;

// 用vector模拟栈，因为栈访问下层元素不大方便
public class InfoStack {
    private Vector<Info> stk;

    public InfoStack() {
        stk = new Vector<>();
    }

    public void Init() {
        Info i = new Info();
        stk.add(i);
    }

    public void Push(Info i) {
        stk.add(i);
    }

    public void Pop() {
        stk.remove(stk.size() - 1);
    }

    public Info Top() {
        return stk.lastElement();
    }

    public boolean IsLast() {
        if (stk.size() == 1) return true;
        else return false;
    }

    public InfoStack Copy() {
        //System.out.println("start one copy");
        InfoStack n = new InfoStack();
        for (Info i : stk) {
            n.Push(i.Copy());
        }
        return n;
    }
}
