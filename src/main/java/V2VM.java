import cs132.util.ProblemException;
import cs132.vapor.ast.*;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VBuiltIn.Op;
import visitors.VisitorLiveness;
import visitors.VisitorV2VM;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;


public class V2VM {

    public static void main(String[] args){
        VaporProgram program = null;
        try {
            program = parseVapor(System.in, System.err);


        }
        catch (IOException e ) {
            e.printStackTrace();
        }

        for(VDataSegment vDataSegment: program.dataSegments){
            System.out.println(vDataSegment.ident+":");
            for(VOperand.Static value:vDataSegment.values){
                System.out.printf("\t%s\n",value.toString());
            }
        }
        System.out.println();

        VisitorLiveness[] livenessVisitors = new VisitorLiveness[program.functions.length];

        for(int i = 0; i < program.functions.length; i++){
            livenessVisitors[i] = new VisitorLiveness(program.functions[i]);
        }

        VisitorV2VM visitorV2VM = new VisitorV2VM(livenessVisitors);

        for(int i = 0; i < program.functions.length; i++){
            visitorV2VM.output(i, program.functions[i]);
        }




    }

    public static VaporProgram parseVapor(InputStream in, PrintStream err)
            throws IOException {
        Op[] ops = {
                Op.Add, Op.Sub, Op.MulS, Op.Eq, Op.Lt, Op.LtS,
                Op.PrintIntS, Op.HeapAllocZ, Op.Error,
        };
        boolean allowLocals = true;
        String[] registers = null;
        boolean allowStack = false;

        VaporProgram program;
        try {
            program = VaporParser.run(new InputStreamReader(in), 1, 1,
                    java.util.Arrays.asList(ops),
                    allowLocals, registers, allowStack);
        } catch (ProblemException ex) {
            err.println(ex.getMessage());
            return null;
        }

        return program;
    }


}
