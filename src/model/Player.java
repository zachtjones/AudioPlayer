package model;
import java.io.File;
import java.io.IOException;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import runner.AiffRunner;
import runner.AudioRun;
import runner.MP3Runner;
import runner.WAVRunner;

public class Player implements Observable, AudioRun {

	private InvalidationListener observer;
	private String filename;
	private String shortFilename;
	private AudioRun runner;

	/** Holds if a file is being played */
	private boolean isPlaying;

	public Player(){
		isPlaying = false;

	}

	/**
	 * Opens the audio file.
	 * @param filename The audio file's full name to open.
	 */
	public void openFile(File filename) throws IOException {
		if(this.runner != null){
			//close old file
			this.runner.close();
		}
		this.filename = filename.getAbsolutePath();
		this.shortFilename = filename.getName();
		System.out.println("Opening file: " + this.filename);

		if(filename.getName().endsWith(".wav")){
			this.runner = new WAVRunner(filename.getAbsolutePath(), this);
		} else if(filename.getName().endsWith(".aif")){
			this.runner = new AiffRunner(filename.getAbsolutePath(), this);
		} else if(filename.getName().endsWith(".aiff")){
			this.runner = new AiffRunner(filename.getAbsolutePath(), this);
		} else if(filename.getName().endsWith(".mp3")){
			this.runner = new MP3Runner(filename.getAbsolutePath(), this);
			//TODO add more things
		} else {
			throw new IOException("Only the .mp3, .wav, .aiff, and .aif file formats are allowed at this time," + 
					" the others are still in development.");
		}

		stateChanged();
	}

	/**
	 * Plays the file.
	 * If the end was reached, this will play from the beginning.
	 * Only call if canPlay() returns true.
	 */
	@Override
	public void play(){
		this.runner.play();
		this.isPlaying = true;
		stateChanged();
	}

	/** Gets if you can play the file */
	public boolean canPlay(){
		return filename != null && !isPlaying;
	}

	/**
	 * Pauses the file.
	 * When play is called after this, the audio should continue where it was paused.
	 * Only call if canPause() returns true.
	 */
	@Override
	public void pause(){
		this.runner.pause();
		this.isPlaying = false;
		stateChanged();
	}

	/** Gets if you can pause the file */
	public boolean canPause(){
		return filename != null && isPlaying;
	}

	/**
	 * Stops running the file.
	 * When play is called after this, the audio should continue from the start.
	 * Only call if canStop() returns true.
	 */
	@Override
	public void stop(){
		this.runner.stop();
		this.isPlaying = false;
		stateChanged();
	}

	/** Gets if you can stop the file */
	public boolean canStop(){
		//same result as being able to pause it
		return canPause();
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

	/** Call when the state is changed of this. 
	 * Call this on the GUI thread */
	public void stateChanged(){
		if(this.observer == null){ return; }
		//call invalidated on the observer
		this.observer.invalidated(this);
	}

	/**
	 * Gets the time that this is currently at.
	 * @return An int that is the time in seconds in the audio file, 
	 * or -1 if there is an error.
	 */
	@Override
	public int getTime() {
		if(this.runner == null){return -1; }
		return this.runner.getTime();
	}

	/**
	 * Gets the length of time for the audio file.
	 * @return An int that is the time in seconds of the length 
	 * of the audio in this file, or -1 if there is an error.
	 */
	@Override
	public int getLength() {
		if(this.runner == null){return -1; }
		return this.runner.getLength();
	}
	
	/** Gets the info (number of channels and the sample rate) as a String */
	public String getInfo(){
		if(this.runner == null){return ""; }
		return this.runner.toString();
	}
	
	/** Closes the runner */
	@Override
	public void close(){
		if(this.runner != null){
			this.runner.close();
		}
	}

	@Override
	public boolean isAtEnd() {
		if(this.runner == null){ return false; }
		//delegate down
		return runner.isAtEnd();
	}

}
