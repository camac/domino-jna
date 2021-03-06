package com.mindoo.domino.jna.internal;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;

public interface INotesNativeAPI32V1000 extends Library {

	@UndocumentedAPI
	public short NSFQueryDB(int hDb, Memory query, int flags,
			int maxDocsScanned, int maxEntriesScanned, int maxMsecs,
			IntByReference retResults, IntByReference retError, IntByReference retExplain);

	@UndocumentedAPI
	public short NSFGetSoftDeletedViewFilter(int hViewDB, int hDataDB, int viewNoteID, IntByReference hFilter);

	@UndocumentedAPI
	public short NSFDbLargeSummaryEnabled(int hDB);

	@UndocumentedAPI
	public short NSFDesignHarvest (int hDB, int flags);

}
