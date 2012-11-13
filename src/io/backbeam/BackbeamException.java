package io.backbeam;

public class BackbeamException extends RuntimeException {

	private static final long serialVersionUID = 193488235052779390L;
	
	public BackbeamException(Throwable throwable) {
		super(throwable);
	}

	public BackbeamException(String string) {
		super(string);
	}

}
