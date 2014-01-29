package io.backbeam;

import java.io.File;
import java.io.InputStream;

public class FileUpload {
	
	private String mimeType;
	private String filename;
	private File file;
	private InputStream inputStream;
	
	public FileUpload(File file, String mimeType) {
		this.file = file;
		this.mimeType = mimeType;
	}
	
	public FileUpload(InputStream inputStream, String filename, String mimeType) {
		this.inputStream = inputStream;
		this.filename = filename;
		this.mimeType = mimeType;
	}

	public String getMimeType() {
		return mimeType;
	}

	public String getFilename() {
		return filename;
	}

	public File getFile() {
		return file;
	}

	public InputStream getInputStream() {
		return inputStream;
	}

}
