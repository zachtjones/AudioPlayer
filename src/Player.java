import java.io.File;

import javafx.beans.InvalidationListener;
import javafx.beans.Observable;

public class Player implements Observable {
	
	private InvalidationListener observer;
	private String filename;
	private String shortFilename;
	
	public Player(){
		
	}
	
	/**
	 * Opens the audio file
	 * @param filename
	 */
	public void openFile(File filename){
		this.filename = filename.getAbsolutePath();
		this.shortFilename = filename.getName();
		System.out.println("Opening file: " + this.filename);
		
		//TODO open the file, use a thread to load the information
		
		
		
		stateChanged();
	}
	
	/** Gets the filename of the audio loaded */
	public String getFilename(){
		return this.shortFilename;
	}

	@Override
	public void addListener(InvalidationListener listener) {
		this.observer = listener;
	}

	@Override
	public void removeListener(InvalidationListener listener) {
		this.observer = null;
	}
	
	private void stateChanged(){
		if(this.observer == null){ return; }
		//call invalidated on the observer
		this.observer.invalidated(this);
	}

}
