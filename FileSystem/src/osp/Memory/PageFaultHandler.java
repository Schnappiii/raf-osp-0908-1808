package osp.Memory;
import java.awt.Frame;
import java.util.*;
import osp.Hardware.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.FileSys.FileSys;
import osp.FileSys.OpenFile;
import osp.IFLModules.*;
import osp.Interrupts.*;
import osp.Utilities.*;
import osp.IFLModules.*;

/**
    The page fault handler is responsible for handling a page
    fault.  If a swap in or swap out operation is required, the page fault
    handler must request the operation.

    @OSPProject Memory
*/
public class PageFaultHandler extends IflPageFaultHandler
{
    /**
        This method handles a page fault. 

        It must check and return if the page is valid, 

        It must check if the page is already being brought in by some other
	thread, i.e., if the page's has already pagefaulted
	(for instance, using getValidatingThread()).
        If that is the case, the thread must be suspended on that page.
        
        If none of the above is true, a new frame must be chosen 
        and reserved until the swap in of the requested 
        page into this frame is complete. 

	Note that you have to make sure that the validating thread of
	a page is set correctly. To this end, you must set the page's
	validating thread using setValidatingThread() when a pagefault
	happens and you must set it back to null when the pagefault is over.

        If a swap-out is necessary (because the chosen frame is
        dirty), the victim page must be dissasociated 
        from the frame and marked invalid. After the swap-in, the 
        frame must be marked clean. The swap-ins and swap-outs 
        must are preformed using regular calls read() and write().

        The student implementation should define additional methods, e.g, 
        a method to search for an available frame.

	Note: multiple threads might be waiting for completion of the
	page fault. The thread that initiated the pagefault would be
	waiting on the IORBs that are tasked to bring the page in (and
	to free the frame during the swapout). However, while
	pagefault is in progress, other threads might request the same
	page. Those threads won't cause another pagefault, of course,
	but they would enqueue themselves on the page (a page is also
	an Event!), waiting for the completion of the original
	pagefault. It is thus important to call notifyThreads() on the
	page at the end -- regardless of whether the pagefault
	succeeded in bringing the page in or not.

        @param thread the thread that requested a page fault
        @param referenceType whether it is memory read or write
        @param page the memory page 

	@return SUCCESS is everything is fine; FAILURE if the thread
	dies while waiting for swap in or swap out or if the page is
	already in memory and no page fault was necessary (well, this
	shouldn't happen, but...). In addition, if there is no frame
	that can be allocated to satisfy the page fault, then it
	should return NotEnoughMemory

        @OSPProject Memory
    */
    public static int do_handlePageFault(ThreadCB thread, 
					 int referenceType,
					 PageTableEntry page)
    {
        if(page.isValid())
        {
        	ThreadCB.dispatch();
        	return FAILURE;
        }
        
        FrameTableEntry frame = null;
        page.setValidatingThread(thread);
        
        for(int i = 0; i < MMU.getFrameTableSize(); ++i)
        {
        	FrameTableEntry f = MMU.getFrame(i);
        	if(!(f.isReferenced() || f.isReserved()))
        	{
        		frame = f;
        		f.setReserved(thread.getTask());
        		break;
        	}
        }
        
        if(frame == null)
        {
        	page.setValidatingThread(null);
        	ThreadCB.dispatch();
        	return NotEnoughMemory;
        }
        
        SystemEvent sevent = new SystemEvent("PageFault");
        thread.suspend(sevent);
        
        if(frame.getPage() != null)
        {
        	PageTableEntry oldPage = frame.getPage();
        	if(frame.isDirty())
        	{
        		
        		thread.getTask().getSwapFile().write(frame.getID(), oldPage, thread);
        		oldPage.setValid(false);
        		frame.setDirty(false);
        		
        		if(thread.getStatus() == ThreadKill)
        		{
        			page.setValidatingThread(null);
        			ThreadCB.dispatch();
        			return FAILURE;
        		}
        	}
        	frame.setPage(null);
        }
        
    	frame.setReferenced(false);
        
        thread.getTask().getSwapFile().read(page.getID(), page, thread);
        if(thread.getStatus() == ThreadKill)
        {
        	frame.setDirty(false);
        	ThreadCB.dispatch();
        	return FAILURE;
        }
        
        frame.setDirty(referenceType == MemoryWrite);
        frame.setPage(page);
        page.setValid(true);
        page.setValidatingThread(null);
        
        sevent.notifyThreads();
        page.notifyThreads();
        ThreadCB.dispatch();
        
        return SUCCESS;
    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
