import cs132.util.ProblemException;
import cs132.vapor.ast.VDataSegment;
import cs132.vapor.ast.VFunction;
import cs132.vapor.ast.VInstr;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VaporProgram;
import cs132.vapor.ast.VBuiltIn.Op;
import visitors.VisitorLiveness;

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

        VisitorLiveness visitorLiveness = new VisitorLiveness();

        for(VFunction function: program.functions){
            System.out.println("index:"+function.body);
            for(VInstr instr: function.body){
                visitorLiveness.reset();
                instr.accept(visitorLiveness);
            }
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
