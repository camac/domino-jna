package com.mindoo.domino.jna.internal.structs.compoundtext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.List;

import com.mindoo.domino.jna.IAdaptable;
import com.mindoo.domino.jna.internal.structs.BaseStructure;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
/**
 * This file was autogenerated by <a href="http://jnaerator.googlecode.com/">JNAerator</a>,<br>
 * a tool written by <a href="http://ochafik.com/">Olivier Chafik</a> that <a href="http://code.google.com/p/jnaerator/wiki/CreditsAndLicense">uses a few opensource projects.</a>.<br>
 * For help, please visit <a href="http://nativelibs4java.googlecode.com/">NativeLibs4Java</a> , <a href="http://rococoa.dev.java.net/">Rococoa</a>, or <a href="http://jna.dev.java.net/">JNA</a>.
 */
public class NotesCDEmbeddedCtlStruct extends BaseStructure implements IAdaptable {
	// C type : WSIG */
	
	public short Signature; /* ORed with WORDRECORDLENGTH */
	public short Length;    /* (length is inclusive with this struct) */
	
	public int CtlStyle;
	public short Flags;
	public short Width;
	public short Height;
	public short Version;
	public short CtlType;
	public short MaxChars;
	public short MaxLines;
	public short Percentage;
	/** C type : DWORD[3] */
	public int[] Spare = new int[3];
	
	public NotesCDEmbeddedCtlStruct() {
		super();
	}
	
	public static NotesCDEmbeddedCtlStruct newInstance() {
		return AccessController.doPrivileged(new PrivilegedAction<NotesCDEmbeddedCtlStruct>() {

			@Override
			public NotesCDEmbeddedCtlStruct run() {
				return new NotesCDEmbeddedCtlStruct();
			}
		});
	}

	protected List<String> getFieldOrder() {
		return Arrays.asList("Signature", "Length", "CtlStyle", "Flags", "Width", "Height", "Version", "CtlType", "MaxChars", "MaxLines", "Percentage", "Spare");
	}
	
	public NotesCDEmbeddedCtlStruct(Pointer peer) {
		super(peer);
	}

	public static NotesCDEmbeddedCtlStruct newInstance(final Pointer peer) {
		return AccessController.doPrivileged(new PrivilegedAction<NotesCDEmbeddedCtlStruct>() {

			@Override
			public NotesCDEmbeddedCtlStruct run() {
				return new NotesCDEmbeddedCtlStruct(peer);
			}
		});
	}

	public static class ByReference extends NotesCDEmbeddedCtlStruct implements Structure.ByReference {
		
	};
	
	public static class ByValue extends NotesCDEmbeddedCtlStruct implements Structure.ByValue {
		
	};
	
	@Override
	public <T> T getAdapter(Class<T> clazz) {
		if (clazz == NotesCDEmbeddedCtlStruct.class) {
			return (T) this;
		}
		return null;
	}

}