package com.mindoo.domino.jna.utils;

import com.mindoo.domino.jna.NotesDatabase;
import com.mindoo.domino.jna.NotesNote;
import com.mindoo.domino.jna.NotesUserId;
import com.mindoo.domino.jna.errors.NotesError;
import com.mindoo.domino.jna.errors.NotesErrorUtils;
import com.mindoo.domino.jna.internal.NotesCAPI;
import com.mindoo.domino.jna.internal.NotesJNAContext;
import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;

import lotus.domino.Session;

/**
 * Utility class to work with Notes User ID files and the ID vault
 * 
 * @author Karsten Lehmann
 */
public class IDUtils {

	/**
	 * Will contact the server and locate a vault for <code>userName</code>.<br>
	 * Then extract the ID file from the vault and write it to <code>idPath</code>.<br>
	 * <br>
	 * If successful returns with the vault server name.
	 * 
	 * @param userName Name of user whose ID is being put into vault - either abbreviated or canonical format
	 * @param password Password to id file being uploaded to the vault
	 * @param idPath Path to where the download ID file should be created or overwritten
	 * @param serverName Name of server to contact
	 * @return the vault server name
	 * @throws NotesError in case of problems, e.g. ERR 22792 Wrong Password
	 */
	public static String extractUserIdFromVault(String userName, String password, String idPath, String serverName) {
		return _getUserIdFromVault(userName, password, idPath, null, null, serverName);
	}
	
	/**
	 * Will contact the server and locate a vault for <code>userName</code>.<br>
	 * Then downloads the ID file from the vault and store it in memory.<br>
	 * 
	 * @param userName Name of user whose ID is being put into vault - either abbreviated or canonical format
	 * @param password Password to id file being uploaded to the vault
	 * @param serverName Name of server to contact
	 * @return the in-memory user id
	 * @throws NotesError in case of problems, e.g. ERR 22792 Wrong Password
	 */
	public static NotesUserId getUserIdFromVault(String userName, String password, String serverName) {
		NotesUserId userId;
		if (NotesJNAContext.is64Bit()) {
			LongByReference rethKFC = new LongByReference();
			_getUserIdFromVault(userName, password, null, rethKFC, null, serverName);
			userId = new NotesUserId(rethKFC.getValue());
		}
		else {
			IntByReference rethKFC = new IntByReference();
			_getUserIdFromVault(userName, password, null, null, rethKFC, serverName);
			userId = new NotesUserId(rethKFC.getValue());
		}
		return userId;
	}
	
	/**
	 * Internal helper method to fetch the ID from the ID vault.
	 * 
	 * @param userName Name of user whose ID is being put into vault - either abbreviated or canonical format
	 * @param password Password to id file being uploaded to the vault
	 * @param idPath if not null, path to where the download ID file should be created or overwritten
	 * @param rethKFC64 if not null, returns the hKFC handle to the in-memory id for 64 bit
	 * @param rethKFC32 if not null, returns the hKFC handle to the in-memory id for 32 bit
	 * @param serverName Name of server to contact
	 * @return the vault server name
	 * @throws NotesError in case of problems, e.g. ERR 22792 Wrong Password
	 */
	private static String _getUserIdFromVault(String userName, String password, String idPath, LongByReference rethKFC64, IntByReference rethKFC32, String serverName) {
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		String userNameCanonical = NotesNamingUtils.toCanonicalName(userName);
		Memory userNameCanonicalMem = NotesStringUtils.toLMBCS(userNameCanonical, true);
		Memory passwordMem = NotesStringUtils.toLMBCS(password, true);
		Memory idPathMem = NotesStringUtils.toLMBCS(idPath, true);
		Memory serverNameMem = new Memory(NotesCAPI.MAXPATH);
		{
			Memory serverNameParamMem = NotesStringUtils.toLMBCS(serverName, true);
			if (serverNameParamMem!=null && (serverNameParamMem.size() > NotesCAPI.MAXPATH)) {
				throw new IllegalArgumentException("Servername length cannot exceed MAXPATH ("+NotesCAPI.MAXPATH+" characters)");
			}
			if (serverNameParamMem!=null) {
				byte[] serverNameParamArr = serverNameParamMem.getByteArray(0, (int) serverNameParamMem.size());
				serverNameMem.write(0, serverNameParamArr, 0, serverNameParamArr.length);
			}
			else {
				serverNameMem.setByte(0, (byte) 0);
			}
		}
		
		short result;
		if (NotesJNAContext.is64Bit()) {
			result = notesAPI.b64_SECidfGet(userNameCanonicalMem, passwordMem, idPathMem, rethKFC64, serverNameMem, 0, (short) 0, null);
		}
		else {
			result = notesAPI.b32_SECidfGet(userNameCanonicalMem, passwordMem, idPathMem, rethKFC32, serverNameMem, 0, (short) 0, null);
		}
		NotesErrorUtils.checkResult(result);
		
		int vaultServerNameLength = 0;
		for (int i=0; i<serverNameMem.size(); i++) {
			vaultServerNameLength = i;
			if (serverNameMem.getByte(i) == 0) {
				break;
			}
		}
		
		String vaultServerName = NotesStringUtils.fromLMBCS(serverNameMem, vaultServerNameLength);
		return vaultServerName;
	}
	
	/**
	 * Will open the ID file name provided, locate a vault server for user <code>userName</code>,
	 * upload the ID file contents to the vault, then return with the vault server name.<br>
	 * 
	 * @param session current legacy session
	 * @param userName Name of user whose ID is being put into vault - either abbreviated or canonical format
	 * @param password Password to id file being uploaded to the vault
	 * @param idPath Path to where the download ID file should be created or overwritten
	 * @param serverName Name of server to contact
	 * @return the vault server name
	 * @throws NotesError in case of problems, e.g. ERR 22792 Wrong Password
	 */
	public static String putUserIdIntoVault(Session session, String userName, String password, String idPath, String serverName) {
		return _putUserIdIntoVault(session, userName, password, idPath, null, null, serverName);
	}
	
	/**
	 * Will locate a vault server for user <code>userName</code> and
	 * upload the specified ID contents to the vault, then return with the vault server name.<br>
	 * 
	 * @param session current legacy session
	 * @param userName Name of user whose ID is being put into vault - either abbreviated or canonical format
	 * @param password Password to id file being uploaded to the vault
	 * @param userId user id
	 * @param serverName Name of server to contact
	 * @return the vault server name
	 * @throws NotesError in case of problems, e.g. ERR 22792 Wrong Password
	 */
	public static String putUserIdIntoVault(Session session, String userName, String password, NotesUserId userId, String serverName) {
		LongByReference phKFC64 = null;
		IntByReference phKFC32 = null;
		
		if (userId!=null) {
			if (NotesJNAContext.is64Bit()) {
				phKFC64 = new LongByReference();
				phKFC64.setValue(userId.getHandle64());
			}
			else {
				phKFC32 = new IntByReference();
				phKFC32.setValue(userId.getHandle32());
			}
		}
			
		return _putUserIdIntoVault(session, userName, password, null, phKFC64, phKFC32, serverName);
	}
	
	/**
	 * Will open the ID file name provided, locate a vault server for user <code>userName</code>,
	 * upload the ID file contents to the vault, then return with the vault server name.<br>
	 * 
	 * @param session current legacy session
	 * @param userName Name of user whose ID is being put into vault - either abbreviated or canonical format
	 * @param password Password to id file being uploaded to the vault
	 * @param idPath Path to where the download ID file should be created or overwritten or null to use the in-memory id
	 * @param phKFC64 handle to the in-memory id or null to use an id file on disk for 64 bit
	 * @param phKFC32 handle to the in-memory id or null to use an id file on disk for 32 bit
	 * @param serverName Name of server to contact
	 * @return the vault server name
	 * @throws NotesError in case of problems, e.g. ERR 22792 Wrong Password
	 */
	private static String _putUserIdIntoVault(Session session, String userName, String password, String idPath,
			LongByReference phKFC64, IntByReference phKFC32, String serverName) {
		//opening any database on the server is required before putting the id fault, according to the
		//C API documentation and sample "idvault.c"
		NotesDatabase anyServerDb = new NotesDatabase(session, serverName, "names.nsf");
		try {
			NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
			
			String userNameCanonical = NotesNamingUtils.toCanonicalName(userName);
			Memory userNameCanonicalMem = NotesStringUtils.toLMBCS(userNameCanonical, true);
			Memory passwordMem = NotesStringUtils.toLMBCS(password, true);
			Memory idPathMem = NotesStringUtils.toLMBCS(idPath, true);
			Memory serverNameMem = new Memory(NotesCAPI.MAXPATH);
			{
				Memory serverNameParamMem = NotesStringUtils.toLMBCS(serverName, true);
				if (serverNameParamMem!=null && (serverNameParamMem.size() > NotesCAPI.MAXPATH)) {
					throw new IllegalArgumentException("Servername length cannot exceed MAXPATH ("+NotesCAPI.MAXPATH+" characters)");
				}
				if (serverNameParamMem!=null) {
					byte[] serverNameParamArr = serverNameParamMem.getByteArray(0, (int) serverNameParamMem.size());
					serverNameMem.write(0, serverNameParamArr, 0, serverNameParamArr.length);
				}
				else {
					serverNameMem.setByte(0, (byte) 0);
				}
			}
			
			short result;
			if (NotesJNAContext.is64Bit()) {
				result = notesAPI.b64_SECKFMOpen (phKFC64, idPathMem, passwordMem, NotesCAPI.SECKFM_open_All, 0, null);
			}
			else {
				result = notesAPI.b32_SECKFMOpen (phKFC32, idPathMem, passwordMem, NotesCAPI.SECKFM_open_All, 0, null);
			}
			NotesErrorUtils.checkResult(result);
			
			try {
				if (NotesJNAContext.is64Bit()) {
					result = notesAPI.b64_SECidfPut(userNameCanonicalMem, passwordMem, idPathMem, phKFC64, serverNameMem, 0, (short) 0, null);
				}
				else {
					result = notesAPI.b32_SECidfPut(userNameCanonicalMem, passwordMem, idPathMem, phKFC32, serverNameMem, 0, (short) 0, null);
				}
				NotesErrorUtils.checkResult(result);
			}
			finally {
				if (NotesJNAContext.is64Bit()) {
					result = notesAPI.b64_SECKFMClose(phKFC64, NotesCAPI.SECKFM_close_WriteIdFile, 0, null);
					
				}
				else {
					result = notesAPI.b32_SECKFMClose(phKFC32, NotesCAPI.SECKFM_close_WriteIdFile, 0, null);
				}
				NotesErrorUtils.checkResult(result);
			}
					
			int vaultServerNameLength = 0;
			for (int i=0; i<serverNameMem.size(); i++) {
				if (serverNameMem.getByte(i) == 0) {
					break;
				}
				else {
					vaultServerNameLength = i;
				}
			}
			
			String vaultServerName = NotesStringUtils.fromLMBCS(serverNameMem, vaultServerNameLength);
			return vaultServerName;

		}
		finally {
			anyServerDb.recycle();
		}
	}
	
	/**
	 * Container for the ID vault sync result data
	 * 
	 * @author Karsten Lehmann
	 */
	public static class SyncResult {
		private String m_vaultServer;
		private int m_flags;
		
		private SyncResult(String vaultServer, int flags) {
			m_vaultServer = vaultServer;
			m_flags = flags;
		}
		
		public String getVaultServer() {
			return m_vaultServer;
		}
		
		public boolean isIdSyncDone() {
			return (m_flags & 1) == 1;
		}
		
		public boolean isIdFoundInVault() {
			if ((m_flags & 2) == 2) {
				return true;
			}
			else {
				return false;
			}
		}
	}
	
	/**
	 * Will open the ID file name provided, locate a vault server, synch the ID file contents to the vault,
	 * then return the synched content. If successful the vault server name is returned.

	 * @param userName Name of user whose ID is being put into vault - either abbreviated or canonical format
	 * @param password Password to id file being uploaded to the vault
	 * @param idPath Path to where the download ID file should be created or overwritten
	 * @param serverName Name of server to contact
	 * @return sync result
	 */
	public static SyncResult syncUserIdWithVault(String userName, String password, String idPath, String serverName) {
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		String userNameCanonical = NotesNamingUtils.toCanonicalName(userName);
		Memory userNameCanonicalMem = NotesStringUtils.toLMBCS(userNameCanonical, true);
		Memory passwordMem = NotesStringUtils.toLMBCS(password, true);
		Memory idPathMem = NotesStringUtils.toLMBCS(idPath, true);
		Memory serverNameMem = new Memory(NotesCAPI.MAXPATH);
		{
			Memory serverNameParamMem = NotesStringUtils.toLMBCS(serverName, true);
			if (serverNameParamMem!=null && (serverNameParamMem.size() > NotesCAPI.MAXPATH)) {
				throw new IllegalArgumentException("Servername length cannot exceed MAXPATH ("+NotesCAPI.MAXPATH+" characters)");
			}
			if (serverNameParamMem!=null) {
				byte[] serverNameParamArr = serverNameParamMem.getByteArray(0, (int) serverNameParamMem.size());
				serverNameMem.write(0, serverNameParamArr, 0, serverNameParamArr.length);
			}
			else {
				serverNameMem.setByte(0, (byte) 0);
			}
		}
		
		LongByReference phKFC64 = new LongByReference();
		IntByReference phKFC32 = new IntByReference();
		IntByReference retdwFlags = new IntByReference();
		
		short result;
		if (NotesJNAContext.is64Bit()) {
			result = notesAPI.b64_SECKFMOpen (phKFC64, idPathMem, passwordMem, NotesCAPI.SECKFM_open_All, 0, null);
		}
		else {
			result = notesAPI.b32_SECKFMOpen (phKFC32, idPathMem, passwordMem, NotesCAPI.SECKFM_open_All, 0, null);
		}
		NotesErrorUtils.checkResult(result);
		
		try {
			if (NotesJNAContext.is64Bit()) {
				result = notesAPI.b64_SECidfSync(userNameCanonicalMem, passwordMem, idPathMem, phKFC64, serverNameMem, 0, (short) 0, null, retdwFlags);
			}
			else {
				result = notesAPI.b32_SECidfSync(userNameCanonicalMem, passwordMem, idPathMem, phKFC32, serverNameMem, 0, (short) 0, null, retdwFlags);
			}
			NotesErrorUtils.checkResult(result);
		}
		finally {
			if (NotesJNAContext.is64Bit()) {
				result = notesAPI.b64_SECKFMClose(phKFC64, NotesCAPI.SECKFM_close_WriteIdFile, 0, null);
			}
			else {
				result = notesAPI.b32_SECKFMClose(phKFC32, NotesCAPI.SECKFM_close_WriteIdFile, 0, null);
			}
			NotesErrorUtils.checkResult(result);
		}
	
		NotesErrorUtils.checkResult(result);
		
		int vaultServerNameLength = 0;
		for (int i=0; i<serverNameMem.size(); i++) {
			vaultServerNameLength = i;
			if (serverNameMem.getByte(i) == 0) {
				break;
			}
		}
		
		String vaultServerName = NotesStringUtils.fromLMBCS(serverNameMem, vaultServerNameLength);
		
		SyncResult syncResult = new SyncResult(vaultServerName, retdwFlags.getValue());
		return syncResult;
	}
	
	/**
	 * Resets an ID password. This password is required by the user when they recover their ID file from the ID vault.
	 * 
	 * @param server Name of server to contact to request the password reset. Can be NULL if executed from a program or agent on a server. Does NOT have to be a vault server. But must be running Domino 8.5 or later. 
	 * @param userName Name of user to reset their vault id file password.
	 * @param password New password to set in the vault record for pUserName.
	 * @param downloadCount (max. 65535) If this user's effective policy setting document has "allow automatic ID downloads" set to no, then this parameter specifies how many downloads the user can now perform. If downloads are automatic this setting should be zero.
	 */
	public static void resetUserPasswordInVault(String server, String userName, String password, int downloadCount) {
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		String userNameCanonical = NotesNamingUtils.toCanonicalName(userName);
		Memory userNameCanonicalMem = NotesStringUtils.toLMBCS(userNameCanonical, true);
		Memory passwordMem = NotesStringUtils.toLMBCS(password, true);
		Memory serverNameMem = NotesStringUtils.toLMBCS(server, true);

		short result = notesAPI.SECidvResetUserPassword(serverNameMem, userNameCanonicalMem, passwordMem, (short) (downloadCount & 0xffff), 0, null); 
		NotesErrorUtils.checkResult(result);
	}
	
	/**
	 * This function changes the password in the specified ID file.<br>
	 * You can use this function to change the password in a user's id, a server's id, or a certifier's id.<br>
	 * <br>
	 * Multiple passwords are not supported.
	 * 
	 * @param idPath path to the ID file whose password should be changed
	 * @param oldPassword old password in the ID file.  This parameter can only be NULL if there is no old password.  If this parameter is set to "", then ERR_BSAFE_NULLPARAM is returned
	 * @param newPassword new password on the ID file. If this parameter is NULL, the password is cleared.  If the specified ID file requires a password and this parameter is NULL, then ERR_BSAFE_PASSWORD_REQUIRED is returned.  If this parameter is set to "", then ERR_BSAFE_NULLPARAM is returned.  If the specified ID file is set for a minimum password length and this string contains less than that minimum, then ERR_REG_MINPSWDCHARS is returned.
	 */
	public static void changeIDPassword(String idPath, String oldPassword, String newPassword) {
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		Memory idPathMem = NotesStringUtils.toLMBCS(idPath, true);
		Memory oldPasswordMem = NotesStringUtils.toLMBCS(oldPassword, true);
		Memory newPasswordMem = NotesStringUtils.toLMBCS(newPassword, true);

		short result = notesAPI.SECKFMChangePassword(idPathMem, oldPasswordMem, newPasswordMem);
		NotesErrorUtils.checkResult(result);
	}
	
	/**
	 * This function returns the Username associated with the workstation's or server's ID where this function is executed.<br>
	 * 
	 * @return username
	 */
	public static String getCurrentUsername() {
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		Memory retUserNameMem = new Memory(NotesCAPI.MAXUSERNAME+1);
		
		short result = notesAPI.SECKFMGetUserName(retUserNameMem);
		NotesErrorUtils.checkResult(result);
		
		int userNameLength = 0;
		for (int i=0; i<retUserNameMem.size(); i++) {
			userNameLength = i;
			if (retUserNameMem.getByte(i) == 0) {
				break;
			}
		}
		
		String userName = NotesStringUtils.fromLMBCS(retUserNameMem, userNameLength);
		return userName;
	}
	
	/**
	 * This function switches to the specified ID file and returns the user name associated with it.<br>
	 * <br>
	 * Multiple passwords are not supported.<br>
	 * <br>
	 * NOTE: This function should only be used in a C API stand alone application.
	 * 
	 * @param idPath path to the ID file that is to be switched to
	 * @param password password of the ID file that is to be switched to
	 * @param dontSetEnvVar  If specified, the notes.ini file (either ServerKeyFileName or KeyFileName) is modified to reflect the ID change.
	 * @return user name, in the ID file that is to be switched to
	 */
	public static String switchToId(String idPath, String password, boolean dontSetEnvVar) {
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		Memory idPathMem = NotesStringUtils.toLMBCS(idPath, true);
		Memory passwordMem = NotesStringUtils.toLMBCS(password, true);
		Memory retUserNameMem = new Memory(NotesCAPI.MAXUSERNAME+1);
		
		short result = notesAPI.SECKFMSwitchToIDFile(idPathMem, passwordMem, retUserNameMem,
				NotesCAPI.MAXUSERNAME, dontSetEnvVar ? NotesCAPI.fKFM_switchid_DontSetEnvVar : 0, null);
		NotesErrorUtils.checkResult(result);
		
		int userNameLength = 0;
		for (int i=0; i<retUserNameMem.size(); i++) {
			userNameLength = i;
			if (retUserNameMem.getByte(i) == 0) {
				break;
			}
		}
		
		String userName = NotesStringUtils.fromLMBCS(retUserNameMem, userNameLength);
		return userName;
	}
	
	/**
	 * The method tries to open the ID with the specified password. If the password is
	 * not correct, the method tries a {@link NotesError}
	 * 
	 * @param idPath id path
	 * @param password password
	 * @throws NotesError e.g. ERR 6408 if password is incorrect
	 */
	public static void checkIDPassword(String idPath, String password) {
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		Memory idPathMem = NotesStringUtils.toLMBCS(idPath, true);
		Memory passwordMem = NotesStringUtils.toLMBCS(password, true);
		
		short result;
		if (NotesJNAContext.is64Bit()) {
			LongByReference phKFC64 = new LongByReference();
			result = notesAPI.b64_SECKFMOpen(phKFC64, idPathMem, passwordMem, 0, 0, null);
			NotesErrorUtils.checkResult(result);

			result = notesAPI.b64_SECKFMClose(phKFC64, 0, 0, null);
			NotesErrorUtils.checkResult(result);
		}
		else {
			IntByReference phKFC32 = new IntByReference();
			result = notesAPI.b32_SECKFMOpen(phKFC32, idPathMem, passwordMem, 0, 0, null);
			NotesErrorUtils.checkResult(result);

			result = notesAPI.b32_SECKFMClose(phKFC32, 0, 0, null);
			NotesErrorUtils.checkResult(result);
		}
	}
	
	/**
	 * Callback interface to work with an opened ID
	 * 
	 * @param <T> computation result type
	 * 
	 * @author Karsten Lehmann
	 */
	public static interface IDAccessCallback<T> {
		
		/**
		 * Implement this method to work with the passed user id. <b>Do not store it anywhere, since it is disposed right after the method call!</b>.
		 * 
		 * @param id id
		 * @return optional computation result
		 * @throws Exception in case of errors
		 */
		public T accessId(NotesUserId id) throws Exception;
		
	}
	
	/**
	 * Opens an ID file and returns an in-memory handle for signing ({@link NotesNote#sign(NotesUserId, boolean)})
	 * and using note encrypting ({@link NotesNote#copyAndEncrypt(NotesUserId, java.util.EnumSet)} /
	 * {@link NotesNote#decrypt(NotesUserId)}).
	 * 
	 * @param <T> optional result type
	 * 
	 * @param idPath id path on disk
	 * @param password id password
	 * @param callback callback code to access the opened ID; we automatically close the ID file when the callback invocation is done
	 * @return optional computation result
	 * @throws Exception in case of errors
	 */
	public static <T> T openUserIdFile(String idPath, String password, IDAccessCallback<T> callback) throws Exception {
		NotesCAPI notesAPI = NotesJNAContext.getNotesAPI();
		
		Memory idPathMem = NotesStringUtils.toLMBCS(idPath, true);
		Memory passwordMem = NotesStringUtils.toLMBCS(password, true);
		
		
		//open the id file
		short result;
		if (NotesJNAContext.is64Bit()) {
			LongByReference phKFC64 = new LongByReference();
			
			result = notesAPI.b64_SECKFMOpen (phKFC64, idPathMem, passwordMem, NotesCAPI.SECKFM_open_All, 0, null);
			NotesErrorUtils.checkResult(result);
			
			try {
				NotesUserId id = new NotesUserId(phKFC64.getValue());
				//invoke callback code
				return callback.accessId(id);
			}
			finally {
				//and close the ID file afterwards
				result = notesAPI.b64_SECKFMClose(phKFC64, 0, 0, null);
				NotesErrorUtils.checkResult(result);
			}
		}
		else {
			IntByReference phKFC32 = new IntByReference();
			
			result = notesAPI.b32_SECKFMOpen(phKFC32, idPathMem, passwordMem, NotesCAPI.SECKFM_open_All, 0, null);
			NotesErrorUtils.checkResult(result);
			
			try {
				NotesUserId id = new NotesUserId(phKFC32.getValue());
				//invoke callback code
				return callback.accessId(id);
			}
			finally {
				//and close the ID file afterwards
				result = notesAPI.b32_SECKFMClose(phKFC32, 0, 0, null);
				NotesErrorUtils.checkResult(result);
			}
		}
	}
}
