package pt.ulisboa.tecnico.cnv.BIT.tools;


import BIT.highBIT.*;
import java.io.File;
import java.util.*;

public class AllocCount
{
    private static HashMap<Long,Integer>  newcount_threadId =  new HashMap<>();
    private static HashMap<Long,Integer>  newarraycount_threadId =  new HashMap<>();
    private static HashMap<Long,Integer>  anewarraycount_threadId=  new HashMap<>();
    private static HashMap<Long,Integer>  multianewarraycount_threadId =  new HashMap<>();

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
                    InstructionArray instructions = routine.getInstructionArray();

                    for (Enumeration instrs = instructions.elements(); instrs.hasMoreElements(); ) {
                        Instruction instr = (Instruction) instrs.nextElement();
                        int opcode=instr.getOpcode();
                        if ((opcode==InstructionTable.NEW) ||
                                (opcode==InstructionTable.newarray) ||
                                (opcode==InstructionTable.anewarray) ||
                                (opcode==InstructionTable.multianewarray)) {
                            instr.addBefore("pt/ulisboa/tecnico/cnv/BIT/tools/AllocCount", "allocCount", new Integer(opcode));
                        }
                    }
                }
                ci.write(out_filename);
            }
        }
    }


    public static synchronized void allocCount(int type)
    {
        int newcount = 0, newarraycount = 0, anewarraycount = 0, multianewarraycount = 0;
        long id = Thread.currentThread().getId();

        switch(type) {
            case InstructionTable.NEW:
                if(newcount_threadId.containsKey(id))
                    newcount = newcount_threadId.get(id);
                newcount++;
                newcount_threadId.put(id,newcount);
                break;
            case InstructionTable.newarray:
                if(newarraycount_threadId.containsKey(id))
                    newarraycount = newarraycount_threadId.get(id);
                newarraycount++;
                newarraycount_threadId.put(id,newarraycount);
                break;
            case InstructionTable.anewarray:
                if(anewarraycount_threadId.containsKey(id))
                    anewarraycount = anewarraycount_threadId.get(id);
                anewarraycount++;
                anewarraycount_threadId.put(id,anewarraycount);
                break;
            case InstructionTable.multianewarray:
                if(multianewarraycount_threadId.containsKey(id))
                    multianewarraycount = multianewarraycount_threadId.get(id);
                multianewarraycount++;
                multianewarraycount_threadId.put(id,multianewarraycount);
                break;
        }
    }

    public static synchronized int getNewCount(long thread_id){
        if(newcount_threadId.containsKey(thread_id))
            return newcount_threadId.get(thread_id);
        return -1;
    }

    public static synchronized int getNewArrayCount(long thread_id){
        if(newarraycount_threadId.containsKey(thread_id))
            return newarraycount_threadId.get(thread_id);
        return -1;
    }

    public static synchronized int getANewArrayCount(long thread_id){
        if(anewarraycount_threadId.containsKey(thread_id))
            return anewarraycount_threadId.get(thread_id);
        return -1;
    }

    public static synchronized int getMultiArrayCount(long thread_id){
        if(multianewarraycount_threadId.containsKey(thread_id))
            return multianewarraycount_threadId.get(thread_id);
        return -1;
    }

    public static synchronized void reset(long thread_id){
        newcount_threadId.remove(thread_id);
        newarraycount_threadId.remove(thread_id);
        anewarraycount_threadId.remove(thread_id);
        multianewarraycount_threadId.remove(thread_id);
    }
}

