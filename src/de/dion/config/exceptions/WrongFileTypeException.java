package de.dion.config.exceptions;

import java.io.FileNotFoundException;

public class WrongFileTypeException extends FileNotFoundException {
	
	public WrongFileTypeException(String s) {
		super(s);
	}
	
	public WrongFileTypeException() {
		super();
	}
	
	/**
	 * lol
	 */
	private static final long serialVersionUID = -1808342222984430961L;
	
}
