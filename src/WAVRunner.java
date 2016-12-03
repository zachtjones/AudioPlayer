import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import javafx.util.converter.ByteStringConverter;

public class WAVRunner implements AudioRun {

	//TODO file format: http://soundfile.sapp.org/doc/WaveFormat/
	
	/** The reader for the file */
	private FileInputStream dis;
	
	private DataInputStream dat;
	/** The filename for the .wav file*/
	private String filename;
	/** Used to represent the 32-bit unsigned int that is the chunk size */
	private long chunkSize;
	
	public WAVRunner(String filename) throws IOException{
		this.filename = filename;
		File f = new File(filename);
		if(!f.exists()){
			throw new FileNotFoundException("The file: " + filename + " does not exist!");
		}
		dis = new FileInputStream(f);
		dat = new DataInputStream(dis);
		//get the header, which is a marking this is a wav file.
		byte[] temp = new byte[4];
		dat.readFully(temp);
		if(!(new String(temp).equals("RIFF"))){
			//the file should start with RIFF
			throw new IOException("Incorrect encoding for the WAV file, should be 'RIFF' encoded.");
		}
		//the chunk size
		//in java, there are no such things as unsigned 32-bit int's
		//so I'm using a long to represent the data
		dat.readFully(temp);
		System.out.println(Arrays.toString(temp));
		this.chunkSize = Converter.toUIntLittleEndian(temp);
		//get the format: should be "WAVE"
		dat.readFully(temp);
		if(!(new String(temp).equals("WAVE"))){
			//the format should be WAVE
			throw new IOException("Incorrect format for the WAV file, should be 'WAVE'");
		}
		
		System.out.println("Size: " + chunkSize);
		
		System.out.println("Constructor finished with no errors");
	}
	
	@Override
	public void play() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void pause() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		try {
			dis.close();
		} catch (IOException e) {
			System.out.println("Unable to close the fileReader for file: " + this.filename);
		}
	}

	@Override
	public int getTime() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getLength() {
		// TODO Auto-generated method stub
		return 0;
	}

}
