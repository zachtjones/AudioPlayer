
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
		result = prime * result + volume;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Note other = (Note) obj;
		if (freq != other.freq)
			return false;
		if (numCycles != other.numCycles)
			return false;
		if (volume != other.volume)
			return false;
		return true;
	}
	
}
