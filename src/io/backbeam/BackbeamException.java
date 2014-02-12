package io.backbeam;

public class BackbeamException extends RuntimeException {

	private static final long serialVersionUID = 4888148994677731920L;
	
	private String status;
	private String errorMessage;
	
	public BackbeamException(Throwable throwable) {
		super(throwable);
	}

	public BackbeamException(String status) {
		super(status);
		this.status = status;
	}

	public BackbeamException(String status, String errorMessage) {
		super(status+": "+errorMessage);
		this.status = status;
		this.errorMessage = errorMessage;
	}

	public String getStatus() {
		return status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

}
