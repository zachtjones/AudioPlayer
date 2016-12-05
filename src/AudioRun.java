
public interface AudioRun {
	/**
	 * Plays the file
	 */
	public void play();
	/**
	 * Pauses the file
	 */
	public void pause();
	/**
	 * Stops the playback of the file.
	 * When play is next clicked, the start of the sound file should be played.
	 */
	public void stop();
	/**
	 * Stops the file (if playing), then releases the file handles 
	 * and performs any memory-cleanup operations as required.
	 * Attempting to play a closed AudioRun should throw an exception.
	 */
	public void close();
	/**
	 * Gets the time that this is currently at.
	 * @return An int that is the time in seconds in the audio file, 
	 * or -1 if there is an error.
	 */
	public int getTime();
	/**
	 * Gets the length of time for the audio file.
	 * @return An int that is the time in seconds of the length 
	 * of the audio in this file, or -1 if there is an error.
	 */
	public int getLength();
	/** Call when the state of the object changes. 
	 * This should call back down to the GUI*/
	public void stateChanged();
}
