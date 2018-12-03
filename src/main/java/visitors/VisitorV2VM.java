
package visitors;

import cs132.vapor.ast.*;
import exceptions.V2VMException;

import java.util.ArrayList;
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
        System.out.printf("func %s [in %d, out %d, local %d]", vFunction.ident, localLiveness.);
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
        restoreCalleeSaved();
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
        System.out.println();
    }

    @Override
    public void visit(VBuiltIn vBuiltIn) throws V2VMException {
        String dest = vBuiltIn.dest.toString();
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

        if(localLiveness.spilled(dest)){
            System.out.printf("\t$v0 = %s\n", srcString.toString());
            System.out.printf("\t%s{%s} = $v0\n", localLiveness.conversion(dest), dest);
        }
        else {
            System.out.printf("\t%s{%s} = %s\n", localLiveness.conversion(dest), dest, srcString.toString());
        }
    }

    @Override
    public void visit(VMemWrite vMemWrite) throws V2VMException {

    }

    @Override
    public void visit(VMemRead vMemRead) throws V2VMException {
        System.out.println();
    }

    @Override
    public void visit(VBranch vBranch) throws V2VMException {
        System.out.println();
    }

    @Override
    public void visit(VGoto vGoto) throws V2VMException {
        System.out.println();
    }

    @Override
    public void visit(VReturn vReturn) throws V2VMException {
        System.out.println();
    }
}
