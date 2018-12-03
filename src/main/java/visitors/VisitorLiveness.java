package visitors;
import cs132.vapor.ast.*;
import exceptions.V2VMException;

import java.util.*;


public class VisitorLiveness extends VInstr.Visitor<V2VMException> {
    private int instrNumber;
    private InstrLine[] instrLines;
    private List<Integer> jumps;
    private VFunction vFunction;

    private Map<String, Interval> liveIntervals;
    private Map<String, String> variableTranslations;

    private SortedSet<String> calleeRegsUsed;
    private SortedSet<String> callerRegsUsed;

    private List<String> needsToBeSpilled;

    private int nIn;
    private int nLocal;
    private int nOut;

    private int nCallee;
    private int nCaller;

    private static class Interval implements Comparable<Interval>{
        String var;
        String reg;
        int start;
        int end;

        Interval(String var, int start, int end){
            this.var = var;
            this.reg = null;
            this.start = start;
            this.end = end;
        }

        @Override
        public int compareTo(Interval o) {
            int returnMe = this.start - o.start;
            return (returnMe == 0) ? this.end - o.end: returnMe;

        }

        public boolean equals(Interval o){
            return this.start == o.start;
        }

        public String toString(){
            return String.format("%s:[%d,%d]", var, start, end);
        }
    }

    private static class IncreasingEndpointComparator implements Comparator<Interval>{
        @Override
        public int compare(Interval o1, Interval o2) {
            int returnMe  = o1.end - o2.end;
            return (returnMe == 0) ? o1.start - o2.start: returnMe;
        }
    }

    private static class InstrLine{
        int lineNumber;
        Set<String> in;
        Set<String> out;
        Set<String> def;
        Set<String> use;
        Set<Integer> succ;



        InstrLine(){
            lineNumber = -1;
            in = new HashSet<>();
            out = new HashSet<>();
            def = new HashSet<>();
            use = new HashSet<>();
            succ = new HashSet<>();
        }

        public String toString(){
              return String.format("in:%s\nout:%s\ndef:%s\nuse:%s\nsucc:%s\n",
                     in.toString(), out.toString(), def.toString(), use.toString(), succ.toString());
        }
    }

    public VisitorLiveness(VFunction vFunction){
        this.instrNumber = 0;
        this.instrLines = new InstrLine[vFunction.body.length];
        for(int i = 0; i<this.instrLines.length; i++){
            this.instrLines[i] = new InstrLine();
        }
        this.jumps = new ArrayList<>();
        this.vFunction = vFunction;

        liveIntervals = new HashMap<>();
        variableTranslations = new HashMap<>();
        needsToBeSpilled = new ArrayList<>();

        calleeRegsUsed = new TreeSet<>();
        callerRegsUsed = new TreeSet<>();

        nIn = vFunction.params.length-4;
        nLocal = 0;
        nOut = 0;

        for(VInstr vInstr: vFunction.body){
            vInstr.accept(this);
        }
        analysis();
        linearScanAllocation();
    }

    public List<String> getCalleeRegsUsed(){
        return new ArrayList<>(calleeRegsUsed);
    }

    public boolean spilled(String varRef){
        return needsToBeSpilled.contains(varRef);
    }

    public String conversion(String varRef){
        if(spilled(varRef)){
            return String.format("local[%d]", nCallee+nCaller+needsToBeSpilled.indexOf(varRef));
        }
        else {
            if (variableTranslations.get(varRef) == null) {
                //if the var is never used/ is in
                return "$v1";
            }
            else {
                return variableTranslations.get(varRef);
            }
        }
    }

    public String paramLocation(int i){
        if(i<4){
            return String.format("$a%d", i);
        }
        else{
            return String.format("in[%d]", i-4);
        }

    }

    private void reInit(int nInstr){
        this.instrNumber = 0;
        this.instrLines = new InstrLine[nInstr];
        for(int i = 0; i<this.instrLines.length; i++){
            this.instrLines[i] = new InstrLine();
        }
    }

    private void addSucc(){
        for(int i = 0; i < instrLines.length-1; i++){
            if(!jumps.contains(i)){
                //lines are succeeded by the next line if its not a jump (a regular instr)
                instrLines[i].succ.add(i+1);
            }
        }
    }

    private void analysis(){
        //liveness analysis
        addSucc();
        boolean changed = false;
        do{
            changed = false;
            for(int i = 0; i<instrLines.length; i++){
                Set<String> newIn = new HashSet<>(instrLines[i].use);
                Set<String> diff = new HashSet<>(instrLines[i].out);
                diff.removeAll(instrLines[i].def);
                newIn.addAll(diff);

                Set<String> newOut = new HashSet<>();
                for(Integer j : instrLines[i].succ){
                    newOut.addAll(instrLines[j].in);
                }

                if(!newIn.equals(instrLines[i].in) || !newOut.equals(instrLines[i].out)) {
                    changed = true;
                }

                instrLines[i].in = newIn;
                instrLines[i].out = newOut;

            }
        }while(changed);

        //live intervals
        for(int i = 0; i < instrLines.length; i++){
            for(String local: instrLines[i].in){
               if(!liveIntervals.containsKey(local)){
                   liveIntervals.put(local, new Interval(local, i,i));
               }
               else{
                   liveIntervals.get(local).end = i;
               }
            }
            for(String local: instrLines[i].out){
                if(!liveIntervals.containsKey(local)){
                    liveIntervals.put(local, new Interval(local, i,i));
                }
                else{
                    liveIntervals.get(local).end = i;
                }
            }
        }
    }

    private void linearScanAllocation(){

        PriorityQueue<Interval> toBeVisited = new PriorityQueue<>(liveIntervals.size());
        for(Interval interval: liveIntervals.values()){
            toBeVisited.add(interval);
        }

        /*for(Interval interval: new TreeSet<>(toBeVisited)){
            System.out.println(interval);
        }*/

        //we need this to be a set, because we have to be able to get the last interval in active
        NavigableSet<Interval> active = new TreeSet<>(new IncreasingEndpointComparator());


        /*PriorityQueue<String> registers = new PriorityQueue<>(Arrays.asList(
                "$s0","$s1","$s2","$s3","$s4","$s5","$s6","$s7",
                "$t0","$t1","$t2","$t3","$t4","$t5","$t6","$t7","$t8","$t9"
        ));*/


        PriorityQueue<String> registers = new PriorityQueue<>(Arrays.asList(
                "$s0"
        ));


        while(!toBeVisited.isEmpty()){
            Interval nextInterval = toBeVisited.poll();

            //intervals that have expired
            while(!active.isEmpty() && active.first().end < nextInterval.start){
                Interval top = active.pollFirst();
                //finalize tentative register allocation
                variableTranslations.put(top.var, top.reg);
                //free up used variable
                registers.add(top.reg);
            }

            //we can add a new mapping with ease
            if(!registers.isEmpty()){
                nextInterval.reg = registers.poll();
                active.add(nextInterval);
            }
            //we need to spill
            else{
                if(active.isEmpty()){
                    needsToBeSpilled.add(nextInterval.var);
                }
                else {
                    Interval endsLast = active.last();
                    //spill the new interval
                    if (endsLast.end <= nextInterval.end) {
                        needsToBeSpilled.add(nextInterval.var);
                    }
                    //spill an old interval
                    else {
                        needsToBeSpilled.add(endsLast.var);
                        active.remove(endsLast);
                        registers.add(endsLast.reg);

                        nextInterval.reg = registers.poll();
                        active.add(nextInterval);
                    }
                }
            }
        }
        //finalize all things left in active
        for(Interval interval: active){
            variableTranslations.put(interval.var, interval.reg);
        }

        //now we will use some local information to calculate the stack space and such

        for(String reg: variableTranslations.values()){
            if(reg.compareTo("$s7") <= 0)
                calleeRegsUsed.add(reg);
            else
                calleeRegsUsed.add(reg);
        }
        nCallee = calleeRegsUsed.size();
        nCaller = callerRegsUsed.size();
        nLocal = nCallee + nCaller + needsToBeSpilled.size();

        /*System.out.println("FUCK");
        System.out.println(needsToBeSpilled.size());
        variableTranslations.forEach((k,v)-> System.out.printf("%s:%s\n",k,v));
        System.out.println();
        needsToBeSpilled.forEach(System.out::println);*/

    }

    public void print(){
        for(int i = 0; i<instrLines.length; i++){
            System.out.printf("%d:%d\n", i, instrLines[i].lineNumber);
            System.out.println(instrLines[i]);
        }
    }

    public void printGaps(){
        Set<String> all = new HashSet<>();
        for(InstrLine instrLine: instrLines){
            all.addAll(instrLine.in);
        }
        for(int i = 0; i<instrLines.length; i++){
            System.out.print(i+":");
            for(String id: all){
                if(instrLines[i].in.contains(id))
                    System.out.print(id+" ");
                else
                    System.out.print("  ");
            }
            System.out.println();
        }
    }

    @Override
    public void visit(VAssign vAssign) throws V2VMException {
        instrLines[instrNumber].lineNumber = vAssign.sourcePos.line;
        //adding lhs to def
        instrLines[instrNumber].def.add(vAssign.dest.toString());
        //adding rhs to use
        if(vAssign.source instanceof VVarRef) {
            instrLines[instrNumber].use.add(vAssign.source.toString());
        }
        instrNumber++;
    }

    @Override
    public void visit(VCall vCall) throws V2VMException {
        instrLines[instrNumber].lineNumber = vCall.sourcePos.line;
        //adding lhs to def
        instrLines[instrNumber].def.add(vCall.dest.toString());
        //adding params to use
        for(VOperand operand : vCall.args){
            if(operand instanceof VVarRef)
                instrLines[instrNumber].use.add(operand.toString());
        }
        int callIn = vCall.args.length-4;
        if(callIn > nOut){
            nOut = callIn;
        }
        instrNumber++;
    }

    @Override
    public void visit(VBuiltIn vBuiltIn) throws V2VMException {
        instrLines[instrNumber].lineNumber = vBuiltIn.sourcePos.line;
        //adding lhs to def
        if(vBuiltIn.dest != null)
            instrLines[instrNumber].def.add(vBuiltIn.dest.toString());
        //adding params to use
        for(VOperand operand : vBuiltIn.args){
            if(operand instanceof VVarRef)
                instrLines[instrNumber].use.add(operand.toString());
        }
        instrNumber++;
    }

    @Override
    public void visit(VMemWrite vMemWrite) throws V2VMException {
        instrLines[instrNumber].lineNumber = vMemWrite.sourcePos.line;
        //adding source to use
        if(vMemWrite.source instanceof VVarRef)
            instrLines[instrNumber].use.add(vMemWrite.source.toString());
        //adding var in mem to use
        if(((VMemRef.Global)vMemWrite.dest).base instanceof VAddr.Var){
            instrLines[instrNumber].use.add(((VMemRef.Global)vMemWrite.dest).base.toString());
        }
        instrNumber++;
    }

    @Override
    public void visit(VMemRead vMemRead) throws V2VMException {
        instrLines[instrNumber].lineNumber = vMemRead.sourcePos.line;
        //adding dest to def
        instrLines[instrNumber].def.add(vMemRead.dest.toString());
        //adding memory value to use
        if(((VMemRef.Global)vMemRead.source).base instanceof VAddr.Var){
            instrLines[instrNumber].use.add(((VMemRef.Global)vMemRead.source).base.toString());
        }
        instrNumber++;
    }

    @Override
    public void visit(VBranch vBranch) throws V2VMException {
        instrLines[instrNumber].lineNumber = vBranch.sourcePos.line;
        if(vBranch.value instanceof VVarRef)
            instrLines[instrNumber].use.add(vBranch.value.toString());
        instrLines[instrNumber].succ.add(vBranch.target.getTarget().instrIndex);
        instrNumber++;
    }

    @Override
    public void visit(VGoto vGoto) throws V2VMException {
        instrLines[instrNumber].lineNumber = vGoto.sourcePos.line;
        int index = ((VAddr.Label<VCodeLabel>)vGoto.target).label.getTarget().instrIndex;
        instrLines[instrNumber].succ.add(index);
        jumps.add(instrNumber);
        instrNumber++;
    }

    @Override
    public void visit(VReturn vReturn) throws V2VMException {
        instrLines[instrNumber].lineNumber = vReturn.sourcePos.line;
        if(vReturn.value instanceof  VVarRef)
            instrLines[instrNumber].use.add(vReturn.value.toString());
        instrNumber++;
    }
}

