package runner;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import model.Converter;
import model.Player;
import model.SoundPlayer;

public class WAVRunner implements AudioRun {

	/** The reader for the file */
	private FileInputStream dis;

	private DataInputStream dat;
	/** The filename for the .wav file*/
	private String filename;
	/** Used to represent the 32-bit unsigned int that is the chunk size */
	private long chunkSize;
	/** The number of channels, 1 is mono, 2 is stereo */
	private int numChannels;
	/** The number of samples per second*/
	private long sampleRate;
	/** The number of bytes per second */
	private long byteRate;
	/** The number of bytes per sample */
	private int bytesPerSample;
	/** The number of bits per sample */
	private int bitsPerSample;

	/** The acutal sound data for the file */
	private byte[][] frames;

	/** The player that makes the sounds */
	private SoundPlayer player;
	
	/** The player that commands this object */
	private Player p;

	public WAVRunner(String filename, Player p) throws IOException{
		this.p = p;
		long time = System.currentTimeMillis();
		//The default byte ordering assumed for WAVE data files is little-endian. 
		//Files written using the big-endian byte ordering scheme have the identifier 
		//	RIFX instead of RIFF.
		
		this.filename = filename;
		File f = new File(filename);
		if(!f.exists()){
			throw new FileNotFoundException("The file: " + filename + " does not exist!");
		}
		dis = new FileInputStream(f);
		dat = new DataInputStream(dis);

		//get the header, which is 36 bytes long.
		byte[] header = new byte[36];
		int indexHeader = 0;
		dat.readFully(header);
		if(!(new String(header, 0, 4).equals("RIFF"))){
			//the file should start with RIFF
			throw new IOException(
					"Incorrect encoding for the WAV file, should be 'RIFF' encoded, but is "
					+ new String(header, 0, 4));
		}
		indexHeader += 4;
		
		//the chunk size
		//in java, there are no such things as unsigned 32-bit int's
		//so I'm using a long to represent the data
		//the bytes for the chunk size are little endian
		this.chunkSize = Converter.toUIntLittleEndian(header, indexHeader);
		indexHeader +=  4;
		
		//get the format: should be "WAVE"
		if(!(new String(header, indexHeader, 4).equals("WAVE"))){
			//the format should be WAVE
			throw new IOException("Incorrect format for the WAV file, should be 'WAVE'");
		}
		indexHeader += 4;

		//next four bytes encode "fmt " - the space is 0x20 (normal ascii space char)
		if(!(new String(header, indexHeader, 4).equals("fmt "))){
			//the format should be WAVE
			throw new IOException("Incorrect marker, should be 'fmt '");
		}
		indexHeader += 4;

		//next 4 bytes specify the format <- 16 is PCM
		if(!(Converter.toUIntLittleEndian(header, indexHeader) == 16L)){
			throw new IOException("The WAV file should be PCM formatted.");
		}
		indexHeader += 4;

		//next 2 bytes specify the compression-should be 1 for wav
		if(!(header[indexHeader] == 1 && header[indexHeader + 1] == 0)){
			throw new IOException("This WAV file is compressed.");
		}
		indexHeader += 2;

		//the next 2 bytes specify the number of channels
		this.numChannels = Byte.toUnsignedInt(header[indexHeader]) + 
				256 * Byte.toUnsignedInt(header[indexHeader + 1]);
		indexHeader += 2;
		
		//the next 4 bytes specify the sample rate
		this.sampleRate = Converter.toUIntLittleEndian(header, indexHeader);
		indexHeader += 4;

		//the next 4 bytes specify the byte rate
		this.byteRate = Converter.toUIntLittleEndian(header, indexHeader);
		indexHeader += 4;

		//the next 2 bytes specify the bytes per sample
		this.bytesPerSample = Byte.toUnsignedInt(header[indexHeader]) + 
				256 * Byte.toUnsignedInt(header[indexHeader + 1]);
		indexHeader += 2;

		//the next 2 bytes specify the bits per sample
		this.bitsPerSample = Byte.toUnsignedInt(header[indexHeader]) + 
				256 * Byte.toUnsignedInt(header[indexHeader + 1]);
		indexHeader += 2;

		//there could be additional chunks defining metadata, don't care about those
		//skip through these chunks, until 'data' chunk is reached
		byte[] temp4 = new byte[4];
		dat.readFully(temp4);
		while(!(new String(temp4).equals("data"))){
			dat.readFully(temp4);
			long tempSize = Converter.toUIntLittleEndian(temp4);
			dat.readFully(new byte[(int)tempSize]);
			dat.readFully(temp4);
		}
		
		//the next 4 bytes are the size of the data chunk
		dat.readFully(temp4);
		long result = Converter.toUIntLittleEndian(temp4);
		//done with the header, move on to the data 

		//calculations to determine the data size
		long totalSamples = result / this.bytesPerSample;

		//set the data's size
		this.frames = new byte[(int)totalSamples][this.bytesPerSample];

		//the rest of the file is the sound data
		//load using another thread
		new Thread(() -> {
			try {
				//read into the frames, each of which is a byte array
				for(int i = 0; i < this.frames.length; i++){
					dat.readFully(this.frames[i]);
				}
				System.out.println("Finished loading");
			} catch (IOException e){
				System.out.println("Error: " + e.getMessage());
			}
			
		}).start();
		
		this.player = new SoundPlayer(frames, this.sampleRate, bitsPerSample, numChannels, false, this);

		System.out.println("Constructor finished with no errors");
		System.out.println("Time: " + (System.currentTimeMillis() - time));
		System.out.println("Byte rate: " + this.byteRate);
		System.out.println("Chunk size: " + this.chunkSize);
	}

	@Override
	public void play() {
		//delegate to player
		player.play();
	}

	@Override
	public void pause() {
		//delegate to player
		player.pause();
	}

	@Override
	public void stop() {
		//delegate to player
		player.stop();
	}

	@Override
	public void close() {
		// want to close the filereaders if the file is not done loading.
		try {
			dat.close();
			dis.close();
		} catch (IOException e) {
			System.err.println("Unable to close the fileReader for file: " + this.filename);
		}
		//close the player
		player.close();
	}

	@Override
	public int getTime() {
		//delegate to player
		return this.player.getTime();
	}

	@Override
	public int getLength() {
		//delegate to player
		return this.player.getLength();
	}

	@Override
	public String toString() {
		return this.numChannels + " channels @" + this.sampleRate + " Hz";
	}

	/** Called by the SoundPlayer */
	public void stateChanged(){
		//carries the change down to the GUI
		p.stateChanged();
	}

	@Override
	public boolean isAtEnd() {
		//the player is at the end of the file
		return player.isAtEnd();
	}

}
