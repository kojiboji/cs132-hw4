package visitors;
import cs132.vapor.ast.*;
import exceptions.V2VMException;


public class VisitorLiveness extends VInstr.Visitor<V2VMException> {
    private int lineNumber;

    public VisitorLiveness(){
        lineNumber = 0;
    }

    public void reset(){
        lineNumber = 0;
    }

    @Override
    public void visit(VAssign vAssign) throws V2VMException {
        System.out.printf("%d:");
    }

    @Override
    public void visit(VCall vCall) throws V2VMException {

    }

    @Override
    public void visit(VBuiltIn vBuiltIn) throws V2VMException {

    }

    @Override
    public void visit(VMemWrite vMemWrite) throws V2VMException {

    }

    @Override
    public void visit(VMemRead vMemRead) throws V2VMException {

    }

    @Override
    public void visit(VBranch vBranch) throws V2VMException {

    }

    @Override
    public void visit(VGoto vGoto) throws V2VMException {

    }

    @Override
    public void visit(VReturn vReturn) throws V2VMException {

    }
}
