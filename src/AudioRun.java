
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
	 * Releases the file handles and performs any memory-cleanup operations as required.
	 * Attempting to play a closed AudioRun should throw an exception.
	 */
	public void close();
}
