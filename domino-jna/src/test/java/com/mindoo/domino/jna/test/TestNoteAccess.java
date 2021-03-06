package com.mindoo.domino.jna.test;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import com.mindoo.domino.jna.NotesAttachment;
import com.mindoo.domino.jna.NotesAttachment.IDataCallback;
import com.mindoo.domino.jna.NotesCollection;
import com.mindoo.domino.jna.NotesDatabase;
import com.mindoo.domino.jna.NotesItem;
import com.mindoo.domino.jna.NotesNote;
import com.mindoo.domino.jna.NotesNote.IItemCallback;
import com.mindoo.domino.jna.constants.Compression;
import com.mindoo.domino.jna.constants.Navigate;
import com.mindoo.domino.jna.constants.OpenNote;
import com.mindoo.domino.jna.constants.UpdateNote;

import lotus.domino.ACL;
import lotus.domino.ACLEntry;
import lotus.domino.Database;
import lotus.domino.DateRange;
import lotus.domino.DateTime;
import lotus.domino.Document;
import lotus.domino.Session;
import lotus.domino.View;

/**
 * Tests cases for note access
 * 
 * @author Karsten Lehmann
 */
public class TestNoteAccess extends BaseJNATestClass {

	/**
	 * The test case opens the database as a user that has read access to
	 * the database and checks whether the NOTE_FLAG_READONLY is set
	 * in the note info accordingly (which indicates that the current user
	 * is not allowed to edit the note).
	 */
//	@Test
	public void testNoteAccess_readOnlyCheck() {
		runWithSession(new IDominoCallable<Object>() {

			@Override
			public Object call(Session session) throws Exception {
				final String userNameReadOnly = "CN=ReadOnly User/O=Mindoo";

				Database dbLegacyAPI = session.getDatabase("", "fakenames.nsf");
				
				boolean aclModified = false;
				
				//tweak the ACL so that the fake user has read access
				ACL acl = dbLegacyAPI.getACL();
				ACLEntry readOnlyEntry = acl.getEntry(userNameReadOnly);
				if (readOnlyEntry==null) {
					readOnlyEntry = acl.createACLEntry(userNameReadOnly, ACL.LEVEL_READER);
					aclModified = true;
				}
				else {
					if (readOnlyEntry.getLevel() != ACL.LEVEL_READER) {
						readOnlyEntry.setLevel(ACL.LEVEL_READER);
						aclModified = true;
					}
				}
				
				if (aclModified) {
					acl.save();
					dbLegacyAPI.recycle();
					dbLegacyAPI = session.getDatabase("", "fakenames.nsf");
				}
				
				NotesDatabase dbData = new NotesDatabase("", "fakenames.nsf", userNameReadOnly);

				View peopleView = dbLegacyAPI.getView("People");
				peopleView.refresh();
				
				//find document with Umlaut values
				Document doc = peopleView.getDocumentByKey("Umlaut", false);

				NotesNote note = dbData.openNoteById(Integer.parseInt(doc.getNoteID(), 16),
						EnumSet.noneOf(OpenNote.class));

				Assert.assertEquals("NotesNote.getUNID returns a correct value", doc.getUniversalID(), note.getUNID());
				
				//check if read-only flag is set as expected
				Assert.assertTrue("The note is read-only for "+userNameReadOnly, note.isReadOnly());
				
				return null;
			}
		});
	}
	
//	@Test
	public void testNoteAccess_openManyNotes() {

		runWithSession(new IDominoCallable<Object>() {

			@Override
			public Object call(Session session) throws Exception {
				System.out.println("Starting note open test");
				
				NotesDatabase dbData = getFakeNamesDb();
				NotesCollection colAllDocs = dbData.openCollectionByName("People");
				colAllDocs.update();
				
				LinkedHashSet<Integer> allIds = colAllDocs.getAllIds(Navigate.NEXT_NONCATEGORY);
				
				System.out.println("Opening "+allIds.size()+" notes");

				Map<Long,String> docDataByHandle = new HashMap<Long, String>();
				List<NotesNote> allNotes = new ArrayList<NotesNote>();
				
				int idx=0;
				for (Integer currNoteId : allIds) {
					NotesNote currNote = dbData.openNoteById(currNoteId.intValue(), EnumSet.noneOf(OpenNote.class));
					allNotes.add(currNote);
					
					String oldData = docDataByHandle.get(currNote.getHandle64());
					if (oldData!=null) {
						System.out.println("old data for handle "+currNote.getHandle64()+": "+oldData);
					}
					String newData = currNote.getItemValueString("lastname") + ", "+currNote.getItemValueString("firstname")+" - noteid "+currNote.getNoteId();
					docDataByHandle.put(currNote.getHandle64(), newData);
					System.out.println(Integer.toString(idx)+" - setting new data to "+newData);
					
					idx++;
				}
				
				System.out.println("Done opening "+allIds.size()+" notes");
				return null;
			}
		});
	
			
	}
	
//	@Test
	public void testNoteAccess_createNote() {
		runWithSession(new IDominoCallable<Object>() {

			@Override
			public Object call(Session session) throws Exception {
				System.out.println("Starting create note test");
				
				NotesDatabase dbData = getFakeNamesDb();
				NotesNote note = dbData.createNote();
				note.setItemValueDateTime("Calendar", Calendar.getInstance());
				note.setItemValueDateTime("JavaDate_Dateonly", new Date(), true, false);
				note.setItemValueDateTime("JavaDate_Timeonly", new Date(), false, true);
				note.setItemValueDateTime("JavaDate_DateTime", new Date(), true, true);
				note.setItemValueDouble("Double", 1.5);
				note.setItemValueString("String", "ABC", true);
				
				System.out.println("Done with create note test");
				return null;
			}
		});
	}
	
	/**
	 * Various checks to make sure that item values and attachments are read
	 * correctly and that the API handles special characters like Umlauts and
	 * newlines the right way.<br>
	 * The method also contains code to check direct attachment streaming functionality.
	 */
	@Test
	public void testNoteAccess_readItems() {
		runWithSession(new IDominoCallable<Object>() {

			@Override
			public Object call(Session session) throws Exception {
				System.out.println("Starting note access test");
				
				NotesDatabase dbData = getFakeNamesDb();
				Database dbLegacyAPI = session.getDatabase(dbData.getServer(), dbData.getRelativeFilePath());
				
				View peopleView = dbLegacyAPI.getView("People");
				peopleView.refresh();
				
				//find document with Umlaut values
				Document doc = peopleView.getDocumentByKey("Umlaut", false);

				//add some fields to check for all data types if missing
				boolean docModified = false;
				
				if (!doc.hasItem("MyNumberList")) {
					Vector<Double> numberValues = new Vector<Double>(Arrays.asList(1.5, 4.5, 9.8, 13.3));
					doc.replaceItemValue("MyNumberList", numberValues);
					docModified=true;
				}

				if (!doc.hasItem("MyDateRange")) {
					DateTime fromDate = session.createDateTime(new Date());
					fromDate.adjustDay(-5);
					DateTime toDate = session.createDateTime(new Date());
					toDate.adjustDay(10);
					
					DateRange range = session.createDateRange(fromDate, toDate);
					doc.replaceItemValue("MyDateRange", range);
					docModified=true;
				}

				if (!doc.hasItem("MyTextWithLineBreak")) {
					doc.replaceItemValue("MyTextWithLineBreak", "line1\nline2\nline3");
					docModified=true;
				}

				if (!doc.hasItem("MyTextListWithLineBreak")) {
					doc.replaceItemValue("MyTextListWithLineBreak", new Vector<String>(Arrays.asList(
							"#1 line1\nline2\nline3",
							"#2 line1\nline2\nline3"
							)));
					docModified=true;
				}

				final int TEST_FILE_SIZE = 1 * 1024 * 1024;
//				final String ITEMNAME_FILES = "rt_Files";
				
				boolean addAttachments = false;
				
				//add some attachments
				Vector<?> attachmentNames = session.evaluate("@AttachmentNames", doc);
				if (attachmentNames==null || attachmentNames.size()==0 || (attachmentNames.size()==1 && "".equals(attachmentNames.get(0)))) {
					addAttachments = true;
				}
				
//				if (!doc.hasItem("MyDateRangeList")) {
//					Vector<DateRange> dateRangeValues = new Vector<DateRange>();
//					{
//						DateTime fromDate = session.createDateTime(new Date());
//						fromDate.adjustDay(-5);
//						DateTime toDate = session.createDateTime(new Date());
//						toDate.adjustDay(10);
//						DateRange range = session.createDateRange(fromDate, toDate);
//						dateRangeValues.add(range);
//					}
//					{
//						DateTime fromDate = session.createDateTime(new Date());
//						fromDate.adjustDay(-30);
//						DateTime toDate = session.createDateTime(new Date());
//						toDate.adjustDay(20);
//						DateRange range = session.createDateRange(fromDate, toDate);
//						dateRangeValues.add(range);
//					}
//					
//					doc.replaceItemValue("MyDateRangeList", dateRangeValues);
//					docModified=true;
//				}

				if (docModified) {
					doc.save(true, false);
				}
				
				NotesNote note = dbData.openNoteById(Integer.parseInt(doc.getNoteID(), 16),
						EnumSet.noneOf(OpenNote.class));
				
				if (addAttachments) {
					for (int i=0; i<3; i++) {
						File currFile = File.createTempFile("test", ".bin");
						
						FileOutputStream fOut = new FileOutputStream(currFile);
						
						for (int j=0; j<TEST_FILE_SIZE; j++) {
							fOut.write(j % 255);
						}
						fOut.flush();
						fOut.close();
						
						//use C-API based attach function to create uncompressed attachments (Java API function would
						//use compression and we cannot read the file starting at a specified offset with compression)
						note.attachFile(currFile.getAbsolutePath(), currFile.getName(), Compression.NONE);
						currFile.delete();
					}
					note.update(EnumSet.noneOf(UpdateNote.class));
				}
				
				note.getItems("$file", new IItemCallback() {

					@Override
					public void itemNotFound() {
						System.out.println("Item not found");
					}

					@Override
					public Action itemFound(NotesItem itemInfo) {
						if (itemInfo.getType() == NotesItem.TYPE_OBJECT) {
							List<Object> values = itemInfo.getValues();
							if (!values.isEmpty()) {
								Object o = values.get(0);
								if (o instanceof NotesAttachment) {
									NotesAttachment att = (NotesAttachment) o;
									final AtomicInteger length = new AtomicInteger();
									
									System.out.println("Reading file "+att.getFileName());

									try {
										final MessageDigest md5_wholefile = MessageDigest.getInstance("md5");
										
										att.readData(new IDataCallback() {

											@Override
											public Action read(byte[] data) {
												length.addAndGet(data.length);
												
												md5_wholefile.update(data);
												
												return Action.Continue;
											}
										});
										Assert.assertEquals("Length correct reading whole file", length.get(), TEST_FILE_SIZE);

										byte[] digestWholeFile = md5_wholefile.digest();
										
										System.out.println("Done reading file, size="+length.get());
										
										if (att.getCompression() == Compression.NONE) {
											final MessageDigest md5_rawrrv = MessageDigest.getInstance("md5");

											length.set(0);
											
											att.readData(new IDataCallback() {

												@Override
												public Action read(byte[] data) {
													length.addAndGet(data.length);
													md5_rawrrv.update(data);

													return Action.Continue;
												}
											}, 0);
											Assert.assertEquals("Length correct reading with offset", length.get(), TEST_FILE_SIZE);
											
											byte[] digestRawRRV = md5_rawrrv.digest();
											Assert.assertArrayEquals("MD5 checksums match for different file read methods", digestWholeFile, digestRawRRV); 
										}
										
										
									}
									catch (NoSuchAlgorithmException t) {
										throw new RuntimeException(t);
									}
								}
							}
						}
						return Action.Continue;
					}
				});
				
				
				{
					//check text items
					String[] textItems = new String[] {"Firstname", "Lastname", "MyTextWithLineBreak"};
//					String[] textItems = new String[] {"MyTextWithLineBreak"};
					
					for (String currItem : textItems) {
						String currItemValueLegacy = doc.getItemValueString(currItem);
						String currItemValueJNA = note.getItemValueString(currItem);
						List<Object> currItemValueJNAGeneric = note.getItemValue(currItem);
						
						Assert.assertNotNull("JNA text value not null for item "+currItem, currItemValueJNA);
						Assert.assertNotNull("JNA generic text value not null for item "+currItem, currItemValueJNAGeneric);
						
						Assert.assertEquals("JNA generic text value has one element for item "+currItem, currItemValueJNAGeneric.size(), 1);
						Assert.assertEquals("JNA generic text value has correct value for item "+currItem, currItemValueLegacy, currItemValueJNAGeneric.get(0));
						
						Assert.assertEquals("JNA text value is correct for item "+currItem, currItemValueLegacy, currItemValueJNA);
					}
				}

				{
					//decode text list item
					String[] textListItems = new String[] {"$UpdatedBy", "MyTextListWithLineBreak"};
//					String[] textListItems = new String[] {"MyTextListWithLineBreak"};
					
					for (String currItem : textListItems) {
						Vector<?> currItemValueLegacy = doc.getItemValue(currItem);
						List<String> currItemValueJNA = note.getItemValueStringList(currItem);
						List<Object> currItemValueGeneric = note.getItemValue(currItem);
						
						Assert.assertNotNull("JNA textlist value not null for item "+currItem, currItemValueJNA);
						Assert.assertNotNull("JNA generic textlist value not null for item "+currItem, currItemValueGeneric);

						Assert.assertEquals("JNA textlist has correct size", currItemValueLegacy.size(), currItemValueJNA.size());
						Assert.assertEquals("JNA generic text list has correct size", currItemValueLegacy.size(), currItemValueGeneric.size());
						
						Assert.assertArrayEquals("JNA textlist has correct content", currItemValueLegacy.toArray(new Object[currItemValueLegacy.size()]), currItemValueJNA.toArray(new Object[currItemValueJNA.size()]));
						Assert.assertArrayEquals("JNA generic textlist has correct content", currItemValueLegacy.toArray(new Object[currItemValueLegacy.size()]), currItemValueGeneric.toArray(new Object[currItemValueJNA.size()]));
					}
				}
				
				{
					//decode number item
					String[] numberItems = new String[] {"RoamCleanPer"};
					
					for (String currItem : numberItems) {
						double currItemValueLegacy = doc.getItemValueDouble(currItem);
						double currItemValueJNA = note.getItemValueDouble(currItem);
						List<Object> currItemValueJNAGeneric = note.getItemValue(currItem);

						Assert.assertNotNull("JNA generic number value not null for item "+currItem, currItemValueJNAGeneric);
						
						Assert.assertEquals("JNA generic number value has one element for item "+currItem, currItemValueJNAGeneric.size(), 1);
						Assert.assertEquals("JNA generic number value has correct value for item "+currItem, Double.valueOf(currItemValueLegacy), currItemValueJNAGeneric.get(0));
						
						Assert.assertEquals("JNA number value is correct for item "+currItem, currItemValueLegacy, currItemValueJNA, 0);
					}
				}
				
				{
					String[] dateItems = new String[] {"HTTPPasswordChangeDate"};
					
					for (String currItem : dateItems) {
						Vector<?> currItemValueLegacy = doc.getItemValueDateTimeArray(currItem);
						Vector<Calendar> convertedLegacyValues = new Vector<Calendar>(currItemValueLegacy.size());
						if (currItemValueLegacy!=null && !currItemValueLegacy.isEmpty()) {
							for (int i=0; i<currItemValueLegacy.size(); i++) {
								Object currObj = currItemValueLegacy.get(i);
								if (currObj instanceof DateTime) {
									DateTime currDateTime = (DateTime) currObj;
									Calendar cal = Calendar.getInstance();
									cal.setTime(currDateTime.toJavaDate());
									convertedLegacyValues.add(cal);
								}
							}
						}
						Calendar currItemValueJNA = note.getItemValueDateTime(currItem);
						List<Object> currItemValueJNAGeneric = note.getItemValue(currItem);
						
						Assert.assertNotNull("Legacy datetime value not null for item "+currItem, currItemValueLegacy);
						Assert.assertNotNull("JNA datetime value not null for item "+currItem, currItemValueJNA);
						Assert.assertNotNull("JNA generic datetime value not null for item "+currItem, currItemValueJNAGeneric);
						
						Assert.assertEquals("Legacy datetime value has one element for item "+currItem, currItemValueLegacy.size(), 1);
						Assert.assertEquals("JNA generic datetime value has one element for item "+currItem, currItemValueJNAGeneric.size(), 1);
						Assert.assertEquals("JNA generic datetime value has correct value for item "+currItem, convertedLegacyValues.get(0), currItemValueJNAGeneric.get(0));
						
						Assert.assertEquals("JNA datetime value is correct for item "+currItem, convertedLegacyValues.get(0), currItemValueJNA);
					}
				}
				
				{
					String[] dateListItems = new String[] {"$Revisions"};
					
					for (String currItem : dateListItems) {
						Vector<?> currItemValueLegacy = doc.getItemValueDateTimeArray(currItem);
						Vector<Calendar> convertedLegacyValues = new Vector<Calendar>(currItemValueLegacy.size());
						if (currItemValueLegacy!=null && !currItemValueLegacy.isEmpty()) {
							for (int i=0; i<currItemValueLegacy.size(); i++) {
								Object currObj = currItemValueLegacy.get(i);
								if (currObj instanceof DateTime) {
									DateTime currDateTime = (DateTime) currObj;
									Calendar cal = Calendar.getInstance();
									cal.setTime(currDateTime.toJavaDate());
									convertedLegacyValues.add(cal);
								}
							}
						}
						List<Object> currItemValueJNAGeneric = note.getItemValue(currItem);
						
						Assert.assertNotNull("JNA generic datetimelist value not null for item "+currItem, currItemValueJNAGeneric);
						Assert.assertEquals("JNA generic datetimelist has correct size", convertedLegacyValues.size(), currItemValueJNAGeneric.size());
						Assert.assertArrayEquals("JNA generic datetimelist has correct content", convertedLegacyValues.toArray(new Object[currItemValueLegacy.size()]), currItemValueJNAGeneric.toArray(new Object[currItemValueJNAGeneric.size()]));
					}
				}

				{
					String[] dateRangeItems = new String[] {"MyDateRange"};
					
					for (String currItem : dateRangeItems) {
						Vector<?> currItemValueLegacy = doc.getItemValue(currItem);
						List<Object> currItemValueJNAGeneric = note.getItemValue(currItem);
						
						Assert.assertNotNull("Legacy value not null for item "+currItem, currItemValueLegacy);

						//the legacy API produces a Vector of two DateTime's for a DateRange
						Assert.assertEquals("Legacy date range values size ok", currItemValueLegacy.size(), 2);
						DateTime rangeStart = (DateTime) currItemValueLegacy.get(0);
						DateTime rangeEnd = (DateTime) currItemValueLegacy.get(1);
						
						Calendar rangeStartCal = Calendar.getInstance();
						rangeStartCal.setTime(rangeStart.toJavaDate());

						Calendar rangeEndCal = Calendar.getInstance();
						rangeEndCal.setTime(rangeEnd.toJavaDate());

						Assert.assertNotNull("JNA generic value not null for item "+currItem, currItemValueJNAGeneric);
						Assert.assertEquals("JNA generic value has correct size for item "+currItem, currItemValueJNAGeneric.size(), 1);
						
						Calendar[] currItemGenericRangeValues = (Calendar[]) currItemValueJNAGeneric.get(0);
						Assert.assertEquals("Start range datetime equal for item "+currItem, rangeStartCal, currItemGenericRangeValues[0]);
						Assert.assertEquals("End range datetime equal for item "+currItem, rangeEndCal, currItemGenericRangeValues[1]);
					}
				}
				
				{
					String itemName = "XXXX";
					
					//round trip check with linebreak
					String osName = System.getProperty("os.name");
					String testVal;
					if (osName.toLowerCase().indexOf("win") >= 0) {
						testVal = "line1\r\nline2\r\nline3";
					}
					else {
						testVal = "line1\nline2\nline3";
					}
					note.setItemValueString(itemName, testVal, true);
					String checkTestVal = note.getItemValueString(itemName);
					
					Assert.assertEquals("setItemValue / getItemValue do not change the value", testVal, checkTestVal);
					
					//check removeItem
					Assert.assertTrue("Note changed with setItemValue", note.hasItem(itemName));
					
					note.removeItem(itemName);
					
					Assert.assertFalse("Item removed by removeItem", note.hasItem(itemName));
				}
				
				
				System.out.println("Done with note access test");
				return null;
			}
		});
	
	}

}
