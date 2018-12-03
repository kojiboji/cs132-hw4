
package visitors;

import cs132.vapor.ast.*;
import exceptions.V2VMException;

import java.util.List;

public class VisitorV2VM extends VInstr.Visitor<V2VMException> {
    VisitorLiveness[] livenessVisitors;
    VisitorLiveness localLiveness;
    VFunction vFunction;

    public VisitorV2VM(VisitorLiveness[] livenessVisitors){
        this.livenessVisitors = livenessVisitors;

    }

    public void output(int index, VFunction function){
        localLiveness = livenessVisitors[index];
        vFunction = function;
        System.out.printf("func %s [in %d, out %d, local %d]\n", vFunction.ident,
                localLiveness.getnIn(), localLiveness.getnOut(), localLiveness.getnLocal());
        saveCalleeSaved();
        loadParams();
        for(int i = 0; i < vFunction.body.length; i++){
            for(VCodeLabel label: vFunction.labels){
                if(label.instrIndex == i) {
                    System.out.println(label.ident+":");
                }
            }
            vFunction.body[i].accept(this);
        }
        System.out.println();
    }

    public void saveCalleeSaved(){
        List<String> calleeSaved = localLiveness.getCalleeRegsUsed();
        for(int i = 0; i < calleeSaved.size(); i++){
            System.out.printf("\tlocal[%d] = %s\n", i, calleeSaved.get(i));
        }
    }

    public void restoreCalleeSaved(){
        List<String> calleeSaved = localLiveness.getCalleeRegsUsed();
        for(int i = 0; i < calleeSaved.size(); i++){
            System.out.printf("\t%s = local[%d]\n", calleeSaved.get(i), i);
        }
    }

    public void saveCallerSaved(){
        List<String> callerSaved = localLiveness.getCallerRegsUsed();
        for(int i = 0; i < callerSaved.size(); i++){
            System.out.printf("\tlocal[%d] = %s\n", i, callerSaved.get(i));
        }
    }

    public void restoreCallerSaved(){
        List<String> callerSaved = localLiveness.getCallerRegsUsed();
        for(int i = 0; i < callerSaved.size(); i++){
            System.out.printf("\t%s = local[%d]\n", callerSaved.get(i), i);
        }
    }

    public void loadParams(){
        for(int i = 0; i < vFunction.params.length; i++){
            if(i<4 || !localLiveness.spilled(vFunction.params[i].ident)) {
                System.out.printf("\t%s{%s} = %s\n",
                        localLiveness.conversion(vFunction.params[i].ident),
                        vFunction.params[i].ident,
                        localLiveness.paramLocation(i));
            }
            //we have memory references
            else {
                System.out.printf("\t$v0 = %s\n", localLiveness.paramLocation(i));
                System.out.printf("\t%s{%s} = $v0\n",
                        localLiveness.conversion(vFunction.params[i].ident),
                        vFunction.params[i].ident);
            }
        }
    }



    @Override
    public void visit(VAssign vAssign) throws V2VMException {
        String dest = vAssign.dest.toString();
        String src = vAssign.source.toString();
        if(vAssign.source instanceof VVarRef){
            src = String.format("%s{%s}", localLiveness.conversion(src), src);
        }
        if(localLiveness.spilled(dest)){
            System.out.printf("\t$v0 = %s\n", src);
            System.out.printf("\t%s{%s} = $v0\n", localLiveness.conversion(dest), dest);
        }
        else{
            System.out.printf("\t%s{%s} = %s\n", localLiveness.conversion(dest), dest, src);
        }
    }

    //eek
    @Override
    public void visit(VCall vCall) throws V2VMException {
        //save caller saved
        saveCallerSaved();
        //load params
        for(int i = 0; i<vCall.args.length; i++){
            VOperand operand = vCall.args[i];
            String value = operand.toString();
            String in = i<4 ? String.format("$a%d", i) : String.format("out[%d]", i-4);
            if(operand instanceof  VVarRef){
                if(localLiveness.spilled(value)){
                    System.out.printf("\t$v0 = %s{%s}\n", localLiveness.conversion(value), value);
                    value = String.format("$v0{%s}", value);
                }
                else{
                    value = String.format("%s{%s}", localLiveness.conversion(value), value);
                }
            }
            System.out.printf("\t%s = %s\n", in, value);
        }
        //call
        String function = vCall.addr.toString();
        if(vCall.addr instanceof VAddr.Var){
            if(localLiveness.spilled(function)){
                System.out.printf("\t$v0 = %s{%s}\n", localLiveness.conversion(function), function);
                function = String.format("$v0{%s}", function);
            }
            else{
                function = String.format("%s{%s}", localLiveness.conversion(function), function);
            }
        }
        System.out.printf("\tcall %s\n", function);

        if(vCall.dest != null)
            System.out.printf("\t%s{%s} = $v0\n", localLiveness.conversion(vCall.dest.toString()), vCall.dest.toString());
        //restore caller saved
        restoreCallerSaved();
    }

    @Override
    public void visit(VBuiltIn vBuiltIn) throws V2VMException {

        StringBuilder srcString = new StringBuilder(vBuiltIn.op.name + "(");
        for(int i = 0; i < vBuiltIn.args.length; i++){
            VOperand arg = vBuiltIn.args[i];
            String ident = arg.toString();
            if(arg instanceof  VVarRef){
                if(localLiveness.spilled(ident)){
                    System.out.printf("\t$v%d = %s{%s}\n", i, localLiveness.conversion(ident), ident);
                    srcString.append(String.format("$v%d{%s} ", i, ident));
                }
                else{
                    srcString.append(String.format("%s{%s} ", localLiveness.conversion(ident), ident));
                }
            }
            else{
                srcString.append(ident+ " ");
            }
        }
        srcString.append(")");

        if(vBuiltIn.dest != null) {
            String dest = vBuiltIn.dest.toString();
            if (localLiveness.spilled(dest)) {
                System.out.printf("\t$v0 = %s\n", srcString.toString());
                System.out.printf("\t%s{%s} = $v0\n", localLiveness.conversion(dest), dest);
            } else {
                System.out.printf("\t%s{%s} = %s\n", localLiveness.conversion(dest), dest, srcString.toString());
            }
        }
        else
            System.out.printf("\t%s\n", srcString.toString());
    }

    @Override
    public void visit(VMemWrite vMemWrite) throws V2VMException {
        String source = vMemWrite.source.toString();
        if(vMemWrite.source instanceof  VVarRef){
            if(localLiveness.spilled(source)){
                System.out.printf("\t$v1 = %s{%s}\n", localLiveness.conversion(source), source);
                source = String.format("$v1{%s}", source);
            }
            else
                source = String.format("%s{%s}", localLiveness.conversion(source), source);
        }

        String base = ((VMemRef.Global)vMemWrite.dest).base.toString();
        if(((VMemRef.Global)vMemWrite.dest).base instanceof VAddr.Var){
            if(localLiveness.spilled(base)){
                System.out.printf("\t$v0 = %s{%s}\n", localLiveness.conversion(base), base);
                base = String.format("$v0{%s}", base);
            }
            else
                base = String.format("%s{%s}", localLiveness.conversion(base), base);
        }

        System.out.printf("\t[%s + %d] = %s\n", base, ((VMemRef.Global) vMemWrite.dest).byteOffset, source);

    }

    @Override
    public void visit(VMemRead vMemRead) throws V2VMException {

        String dest  = vMemRead.dest.toString();
        if(vMemRead.dest instanceof VVarRef){
            if(localLiveness.spilled(dest)){
                System.out.printf("\t$v0 = %s{%s}\n", localLiveness.conversion(dest), dest);
                dest = String.format("$v0{%s}", dest);
            }
            else
                dest = String.format("%s{%s}", localLiveness.conversion(dest), dest);
        }


        String base = ((VMemRef.Global)vMemRead.source).base.toString();
        if(((VMemRef.Global)vMemRead.source).base instanceof VAddr.Var){
            if(localLiveness.spilled(base)){
                System.out.printf("\t$v1 = %s{%s}\n", localLiveness.conversion(base), base);
                base = String.format("$v1{%s}", base);
            }
            else
                base = String.format("%s{%s}", localLiveness.conversion(base), base);
        }

        System.out.printf("\t%s = [%s + %d]\n", dest, base, ((VMemRef.Global) vMemRead.source).byteOffset);

    }

    @Override
    public void visit(VBranch vBranch) throws V2VMException {
        String value = vBranch.value.toString();
        if(vBranch.value instanceof  VVarRef){
            if(localLiveness.spilled(value)) {
                System.out.printf("\t$v0 = %s{%s}\n", localLiveness.conversion(value), value);
                value = String.format("$v0{%s}", value);
            }
            else{
                value = String.format("%s{%s}", localLiveness.conversion(value), value);
            }
        }

        System.out.printf("\t%s %s goto %s\n", vBranch.positive ? "if" : "if0", value, vBranch.target);
    }

    @Override
    public void visit(VGoto vGoto) throws V2VMException {
        System.out.printf("\tgoto %s\n", vGoto.target.toString());
    }

    @Override
    public void visit(VReturn vReturn) throws V2VMException {
        if(vReturn.value != null) {
            String value = vReturn.value.toString();
            if (vReturn.value instanceof VVarRef) {
                System.out.printf("\t$v0 = %s{%s}\n", localLiveness.conversion(value), value);
            }
            restoreCalleeSaved();
            System.out.printf("\tret\n");
        }
        else
            System.out.println("\tret");
    }
}
