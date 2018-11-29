package visitors;
import cs132.vapor.ast.*;
import exceptions.V2VMException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class VisitorLiveness extends VInstr.Visitor<V2VMException> {
    private int instrNumber;
    private InstrLine[] instrLines;

    private static class InstrLine{
        public Set<String> in;
        public Set<String> out;
        public Set<String> def;
        public Set<String> use;
        public Set<Integer> succ;

        public InstrLine(){
            in = new HashSet<>();
            out = new HashSet<>();
            def = new HashSet<>();
            use = new HashSet<>();
            succ = new HashSet<>();
        }

        public String toString(){
              return String.format("in:%s\nout:%s\ndef:%s\nuse:%s\nsucc:%s",
                      in.toString(), out.toString(), def.toString(), use.toString(), succ.toString());
        }
    }

    public VisitorLiveness(){
        this.instrNumber = 0;
        this.instrLines = new InstrLine[0];
    }
    public void reInit(int nInstr){
        this.instrNumber = 0;
        this.instrLines = new InstrLine[nInstr];
        for(int i = 0; i<this.instrLines.length; i++){
            this.instrLines[i] = new InstrLine();
        }
    }

    public void print(){
        for(int i = 0; i<instrLines.length; i++){
            System.out.println(i+":");
            System.out.println(instrLines[i]);
        }
    }


    @Override
    public void visit(VAssign vAssign) throws V2VMException {
        //adding lhs to def
        instrLines[instrNumber].def.add(((VVarRef.Local)vAssign.dest).ident);
        //adding rhs to use
        if(vAssign.source instanceof VVarRef) {
            System.out.println("hello");
            instrLines[instrNumber].use.add(((VVarRef.Local) vAssign.source).ident);
        }
        instrNumber++;
    }

    @Override
    public void visit(VCall vCall) throws V2VMException {
        //adding lhs to def
        instrLines[instrNumber].def.add((vCall.dest).ident);
        //adding params to use
        for(VOperand operand : vCall.args){
            if(operand instanceof VVarRef)
                instrLines[instrNumber].use.add(((VVarRef.Local)operand).ident);
        }

        instrNumber++;
    }

    @Override
    public void visit(VBuiltIn vBuiltIn) throws V2VMException {
        //adding lhs to def
        if(vBuiltIn.dest != null)
            instrLines[instrNumber].def.add(((VVarRef.Local)vBuiltIn.dest).ident);
        //adding params to use
        for(VOperand operand : vBuiltIn.args){
            if(operand instanceof VVarRef)
                instrLines[instrNumber].use.add(((VVarRef.Local)operand).ident);
        }
        instrNumber++;
    }

    //TODO
    @Override
    public void visit(VMemWrite vMemWrite) throws V2VMException {
        //adding source to use
        if(vMemWrite.source instanceof VVarRef)
            instrLines[instrNumber].use.add(((VVarRef.Local)vMemWrite.source).ident);
        //adding var in mem to use
        if(((VMemRef.Global)vMemWrite.dest).base instanceof VAddr.Var){
            instrLines[instrNumber].use.add(
                    ((VVarRef.Local)
                            ((VAddr.Var)
                                    ((VMemRef.Global) vMemWrite.dest).base).var).ident
            );
        }
        instrNumber++;
    }

    @Override
    public void visit(VMemRead vMemRead) throws V2VMException {
        //adding dest to def
        instrLines[instrNumber].def.add(((VVarRef.Local)vMemRead.dest).ident);
        //adding memory value to use
        if(((VMemRef.Global)vMemRead.source).base instanceof VAddr.Var){
            instrLines[instrNumber].use.add(
                    ((VVarRef.Local)
                            ((VAddr.Var)
                                    ((VMemRef.Global) vMemRead.source).base).var).ident
            );
        }
        instrNumber++;
    }

    @Override
    public void visit(VBranch vBranch) throws V2VMException {
        if(vBranch.value instanceof VVarRef)
            instrLines[instrNumber].use.add(((VVarRef.Local)vBranch.value).ident);
        instrLines[vBranch.target.getTarget().instrIndex].succ.add(instrNumber);
        instrNumber++;
    }

    @Override
    public void visit(VGoto vGoto) throws V2VMException {
        int index = ((VAddr.Label<VCodeLabel>)vGoto.target).label.getTarget().instrIndex;
        instrLines[index].succ.add(instrNumber);
        instrNumber++;
    }

    @Override
    public void visit(VReturn vReturn) throws V2VMException {
        instrNumber++;
    }
}

