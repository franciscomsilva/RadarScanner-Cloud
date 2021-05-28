package pt.ulisboa.tecnico.cnv.BIT.tools;


import BIT.highBIT.*;
import java.io.File;
import java.util.*;

public class LoadStoreCount
{
    private static HashMap<Long,Integer> loadcount_threadId = new HashMap<>();
    private static HashMap<Long,Integer> storecount_threadId = new HashMap<>();
    private static HashMap<Long,Integer> fieldloadcount_threadId = new HashMap<>();
    private static HashMap<Long,Integer> fieldstorecount_threadId = new HashMap<>();


    /* main reads in all the files class files present in the input directory,
     * instruments them, and outputs them to the specified output directory.
     * the tools counts the number of field stores, field loads and regular store and loads
     */

    public static void main(String argv[]) {

        File in_dir = new File(argv[0]);
        File out_dir = new File(argv[1]);
        String filelist[] = in_dir.list();

        for (int i = 0; i < filelist.length; i++) {
            String filename = filelist[i];
            if (filename.endsWith(".class")) {
                String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
                ClassInfo ci = new ClassInfo(in_filename);

                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();

                    for (Enumeration instrs = (routine.getInstructionArray()).elements(); instrs.hasMoreElements(); ) {
                        Instruction instr = (Instruction) instrs.nextElement();
                        int opcode=instr.getOpcode();
                        if (opcode !=  InstructionTable.getfield && opcode != InstructionTable.putfield){
                            short instr_type = InstructionTable.InstructionTypeTable[opcode];
                            if (instr_type == InstructionTable.LOAD_INSTRUCTION) {
                                instr.addBefore("pt/ulisboa/tecnico/cnv/BIT/tools/LoadStoreCount", "LSCount", new Integer(0));
                            }
                            else if (instr_type == InstructionTable.STORE_INSTRUCTION) {
                                instr.addBefore("pt/ulisboa/tecnico/cnv/BIT/tools/LoadStoreCount", "LSCount", new Integer(1));
                            }
                        }
                    }
                }
                ci.write(out_filename);
            }
        }

    }



    public static synchronized void LSCount(int type)
    {
        long id = Thread.currentThread().getId();
        int loadcount = 0, storecount = 0;

        if (type == 0) {
            if(loadcount_threadId.containsKey(id))
                loadcount = loadcount_threadId.get(id);
            loadcount++;
            loadcount_threadId.put(id,loadcount);
        }
        else{
            if(storecount_threadId.containsKey(id))
                storecount = storecount_threadId.get(id);
            storecount++;
            storecount_threadId.put(id,storecount);
        }
    }

    public static synchronized int getLoadCount(long thread_id){
        if(loadcount_threadId.containsKey(thread_id))
            return loadcount_threadId.get(thread_id);
        return -1;
    }

    public static synchronized int getStoreCount(long thread_id) {
        if(storecount_threadId.containsKey(thread_id))
            return storecount_threadId.get(thread_id);
        return -1;
    }


    public static synchronized void reset(long thread_id){
       storecount_threadId.remove(thread_id);
       loadcount_threadId.remove(thread_id);
       fieldstorecount_threadId.remove(thread_id);
       fieldloadcount_threadId.remove(thread_id);
    }
}
