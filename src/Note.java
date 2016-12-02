
public class Note {
	
	private int freq;
	private long numCycles;
	private byte volume;
	
	
	public Note(int freq, long numCycles, byte volume) {
		super();
		this.freq = freq;
		this.numCycles = numCycles;
		this.volume = volume;
	}

	/** Gets the frequency of the note, in Hertz*/
	public int getFreq() {
		return freq;
	}

	/** Gets the number of cycles left, in the number of samples left */
	public long getNumCycles() {
		return numCycles;
	}

	/** Gets the volume of this note */
	public byte getVolume() {
		return volume;
	}
	
	/** Decreases the number of cycles left by 1 */
	public void descreaseCycle(){
		this.numCycles --;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + freq;
		result = prime * result + (int) (numCycles ^ (numCycles >>> 32));
		result = prime * result + volume;
		return result;
	}
	
}
