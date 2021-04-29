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
                        if (opcode == InstructionTable.getfield)
                            instr.addBefore("pt/ulisboa/tecnico/cnv/BIT/tools/LoadStoreCount", "LSFieldCount", new Integer(0));
                        else if (opcode == InstructionTable.putfield)
                            instr.addBefore("pt/ulisboa/tecnico/cnv/BIT/tools/LoadStoreCount", "LSFieldCount", new Integer(1));
                        else {
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
                ci.addAfter("pt/ulisboa/tecnico/cnv/BIT/tools/LoadStoreCount", "printLoadStore", "null");
                ci.write(out_filename);
            }
        }

    }

    public static synchronized void LSFieldCount(int type)
    {
        long id = Thread.currentThread().getId();
        int fieldloadcount = 0, fieldstorecount = 0;

        if (type == 0) {
            if (fieldloadcount_threadId.containsKey(id))
                fieldloadcount = fieldloadcount_threadId.get(id);
            fieldloadcount++;
            fieldloadcount_threadId.put(id,fieldloadcount);
        }
        else{
            if(fieldstorecount_threadId.containsKey(id))
                fieldstorecount = fieldstorecount_threadId.get(id);
            fieldstorecount++;
            fieldstorecount_threadId.put(id,fieldstorecount);
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

    public static int getLoadCount(long thread_id){
        if(loadcount_threadId.containsKey(thread_id))
            return loadcount_threadId.get(thread_id);
        return -1;
    }

    public static int getStoreCount(long thread_id) {
        if(storecount_threadId.containsKey(thread_id))
            return storecount_threadId.get(thread_id);
        return -1;
    }

    public static int getFieldLoadCount(long thread_id){
        if(fieldloadcount_threadId.containsKey(thread_id))
            return fieldloadcount_threadId.get(thread_id);
        return -1;
    }

    public static int getFieldStoreCount(long thread_id){
        if(fieldstorecount_threadId.containsKey(thread_id))
            return fieldstorecount_threadId.get(thread_id);
        return -1;
    }

    public static synchronized void reset(long thread_id){
       storecount_threadId.remove(thread_id);
       loadcount_threadId.remove(thread_id);
       fieldstorecount_threadId.remove(thread_id);
       fieldloadcount_threadId.remove(thread_id);
    }
}
