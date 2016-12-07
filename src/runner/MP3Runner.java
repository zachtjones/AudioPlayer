package runner;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import model.Player;
import model.SoundPlayer;

public class MP3Runner implements AudioRun {

	/** The acutal sound data for the file, 
	 * each sample is an array of bytes */
	private byte[][] data;
	
	private long numSamples;
	
	/** The number of samples per second */
	private int sampleRate;
	
	/** The number of audio channels */
	private int numChannels;

	/** The player that makes the sounds */
	private SoundPlayer player;

	/** The player that commands this object */
	private Player p;
	
	
	/** The filename of the file */
	private String filename;
	
	public MP3Runner(String filename, Player p) throws IOException{
		this.filename = filename;
		this.p = p;
		
		File f = new File(filename);
		if(!f.exists()){
			throw new FileNotFoundException("The file: " + filename + " does not exist!");
		}
		
		long fileSize = f.length();
		if(fileSize > Integer.MAX_VALUE){
			//over 2gb for the file, usual size is around 8 mb for ~4 minute song
			throw new IOException("The specified file is too large to read.");
		}
		
		Path path = FileSystems.getDefault().getPath(filename);
		//fastest way to read the contents of the entire file
		//since there are so many calls to reading the file, 
		//it is faster to read the while thing and then parse.
		byte[] fileData = Files.readAllBytes(path);
		System.out.println("Read entire file.");
		
		int index = 0; //the index used for convenience to help decode the file
		
		if(fileData[0] == 0x49 && fileData[1] == 0x44 && fileData[2] == 0x33){
			//TAG v2 structure
			//2 bytes of not important data to play the sound
						
			//next 4 bytes are the size, but the leading bit of each byte is 0, and ignored.
			index = 6;
			//example: 0x0000_0201 is 257 base 10
			int length = 0;
			length |= (int)(fileData[index]) << 21;
			length |= (int)fileData[index + 1] << 14;
			length |= (int)fileData[index + 2] << 7;
			length |= (int)fileData[index + 3]; //the last 7 bits
			index += 4;
			
			//the tag information is not important for reading
			index += length;
			System.out.println("length: " + length);
		} 
		
		//index is at the start of the first frame
		//load the first frame
		System.out.println(index);
		MP3Frame first = new MP3Frame(fileData, index);
		
		
		
		
		//TODO load the file
		
		//http://blog.bjrn.se/2008/10/lets-build-mp3-decoder.html
		//http://www.multiweb.cz/twoinches/mp3inside.htm
		//http://mpgedit.org/mpgedit/mpeg_format/mpeghdr.htm
		//[TAG v2]   Frame1   Frame2   Frame3...   [TAG v1]
		//System.out.println(Arrays.toString(toBits((byte)5, (byte)4)));
		
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
		// no filereaders to close, the constructor closes the files.
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