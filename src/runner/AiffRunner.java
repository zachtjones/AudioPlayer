package runner;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import model.Converter;
import model.Player;
import model.SoundPlayer;

public class AiffRunner implements AudioRun {

	/** The acutal sound data for the file */
	private byte[][] frames;
	/** The number of channels of data */
	private int numChannels;
	/** The number of total samples */
	private long numSamples;
	/** The number of bits in a sample */
	private int bitsPerSample;
	/** The number of samples per second */
	private double sampleRate;

	/** The player that makes the sounds */
	private SoundPlayer player;

	/** The player that commands this object */
	private Player p;
	/** The reader for the file */
	private FileInputStream dis;
	/** The data stream for the file */
	private DataInputStream dat;
	/** The filename of the file */
	private String filename;
	/** The number of bytes per sample */
	private int bytesPerSample;
	
	
	private static long num256_2 = 0x1_0000L;
	private static long num256_3 = 0x100_0000L;
	private static long num256_4 = 0x1_0000_0000L;
	private static long num256_5 = 0x100_0000_0000L;
	private static long num256_6 = 0x1_0000_0000_0000L;
	private static long num256_7 = 0x100_0000_0000_0000L;

	public AiffRunner(String filename, Player p) throws IOException{
		this.p = p;
		this.filename = filename;

		File f = new File(filename);
		if(!f.exists()){
			throw new FileNotFoundException("The file: " + filename + " does not exist!");
		}
		dis = new FileInputStream(f);
		dat = new DataInputStream(dis);

		//big endian file format
		//similiar data format chunk to wav

		//get the header, which is a marking this is a aif file.
		byte[] temp4 = new byte[4];
		dat.readFully(temp4);
		if(!(new String(temp4).equals("FORM"))){
			//the file should start with FORM
			throw new IOException(
					"Incorrect encoding for the AIF file, should be 'FORM' encoded, but is "
							+ new String(temp4));
		}

		//the next 4 bytes are the rest of the file size, which is not needed.
		dat.readFully(temp4);

		//the next 4 bytes are the file marker, "AIFF"
		dat.readFully(temp4);
		if(!(new String(temp4).equals("AIFF"))){
			//the file should be encoded with AIFF
			throw new IOException(
					"Incorrect form type for the AIF file, should be 'AIFF' type, but is "
							+ new String(temp4));
		}

		//the chunk order is not completely defined, 
		//however each file should have the COMM and SSND chunks
		//the other chunks do not need to be interpreted
		while(true){
			dat.readFully(temp4);
			String temp = new String(temp4);
			if(temp.equals("COMM")){
				readCOMM();
			} else if(!temp.equals("SSND")){
				readChunk();
			} else {
				readSSND(); //the sound chunk should be the last one
				break;
			}
		}
		//TODO http://www.muratnkonar.com/aiff/ and http://www.onicos.com/staff/iz/formats/aiff.html
		//http://www.paulbourke.net/dataformats/audio/ and http://www.onicos.com/staff/iz/formats/ieee.c
		//http://stackoverflow.com/questions/35669441/convert-80-bit-extended-precision-in-java

	}

	/** Reads the common chunk of data, indicated by the 'COMM' marker */
	private void readCOMM() throws IOException{
		byte[] temp4 = new byte[4];
		dat.readFully(temp4);
		//the length of the chunk
		long length = Converter.toUIntBigEndian(temp4);

		//the next 2 bytes are the number of channels, big-endian
		byte[] temp2 = new byte[2];
		dat.readFully(temp2);
		this.numChannels = Byte.toUnsignedInt(temp2[0]) * 256 + Byte.toUnsignedInt(temp2[1]);

		//the next 4 are the total number of frames of sound
		dat.readFully(temp4);
		this.numSamples = Converter.toUIntBigEndian(temp4);

		//the next 2 are the bits per sample
		dat.readFully(temp2);
		this.bitsPerSample = Byte.toUnsignedInt(temp2[0]) * 256 + Byte.toUnsignedInt(temp2[1]);
		//calculate bytes per sample
		this.bytesPerSample = (int)Math.ceil(bitsPerSample / 8.0) * this.numChannels;

		//the next 10 bytes are the frame rate as an extended 80-bit floating point number
		//requires some formatting changes to make into a double
		byte[] temp8 = new byte[8];
		dat.readFully(temp2); //the most significant bytes
		dat.readFully(temp8); //the 8 next bytes

		//math to convert to the IEEE double type, which is supported in java
		long high = Byte.toUnsignedLong(temp2[0]) * 256 + Byte.toUnsignedLong(temp2[1]);
		long low = Byte.toUnsignedLong(temp8[0]) * num256_7 + Byte.toUnsignedLong(temp8[1]) * num256_6 + 
				Byte.toUnsignedLong(temp8[2]) * num256_5 + Byte.toUnsignedLong(temp8[3]) * num256_4 + 
				Byte.toUnsignedLong(temp8[4]) * num256_3 + Byte.toUnsignedLong(temp8[5]) * num256_2 + 
				Byte.toUnsignedLong(temp8[6]) * 256 + Byte.toUnsignedLong(temp8[7]);
		long e = (((high & 0x7FFFL) - 16383) + 1023) & 0x7FFL;
		long ld = ((high & 0x8000L) << 48) | (e << 52) | ((low >>> 11) & 0xF_FFFF_FFFF_FFFFL);
		this.sampleRate = Double.longBitsToDouble(ld);
		
		if(length > 18){
			// the length should be 18, but in case more fields are added later, skip over it
			byte[] tempbytes = new byte[(int) (length - 18)];
			dat.readFully(tempbytes);
		}
		
		//set the data's size
		this.frames = new byte[(int)numSamples][this.bytesPerSample];
		//initialize the player
		this.player = new SoundPlayer(frames, (float)this.sampleRate, bitsPerSample, numChannels, true, this);
	}

	/** Reads the sound chunk of data, indicated by the 'SSND' marker
	 * This should be the last chunk of data.
	 * Uses a secondary thread to load the data 
	 * @throws IOException */
	private void readSSND() throws IOException{
		if(this.player == null){
			throw new IOException("The COMM chunk should precede the SSND chunk.");
		}
		
		//load on a new thread
		new Thread(() -> {
			try {
				//the next 4 bytes are the size, not needed as the array length is already set
				byte[] temp4 = new byte[4];
				dat.readFully(temp4);

				for(int i = 0; i < this.frames.length; i++){
					//read in the sound data
					dat.readFully(this.frames[i]);
				}
				
				System.out.println("Finished loading file properly");
			} catch(IOException e){
				System.out.println("Error loading file: " + e.getMessage());
			}
			
		}).start();
		
	}

	/** Reads the next chunk of data.
	 * The result is not interpreted. */
	private void readChunk() throws IOException{
		byte[] temp4 = new byte[4];
		dat.readFully(temp4);
		//the length of the chunk
		long length = Converter.toUIntBigEndian(temp4);
		//read into the next part, but don't need to work with it
		byte[] waste = new byte[(int)length];
		dat.readFully(waste);

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
		//delegate ot player
		return this.player.getLength();
	}

	@Override
	public void stateChanged() {
		//echo back
		p.stateChanged();
	}

	@Override
	public boolean isAtEnd() {
		//delegate to player
		return player.isAtEnd();
	}

	@Override
	public String toString() {
		return this.numChannels + " channels @" + this.sampleRate + " Hz";
	}
}
