package runner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import model.Player;
import model.SoundPlayer;
import runner.MP3Frame.ChannelMode;

public class MP3Runner implements AudioRun {

	/** The acutal sound data for the file, 
	 * each sample is an array of bytes */
	private byte[][] data;
		
	/** The number of samples per second */
	private int sampleRate;
	
	/** The number of audio channels */
	private int numChannels;

	/** The player that makes the sounds */
	private SoundPlayer player;

	/** The player that commands this object */
	private Player p;
	
	/**
	 * Constructor for a mp3 runner. 
	 * This will load the header information on this thread, and start a new one to decode the data.
	 * @param filename The filename to read
	 * @param p The player that constructed this object
	 * @throws IOException If there is an issue reading the file, or incorrect formatting.
	 */
	public MP3Runner(String filename, Player p) throws IOException{
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
			System.out.println("length of tag: " + length);
		} 
		
		//index is at the start of the first frame
		//load the first frame
		System.out.println("Byte index of first frame: " + index);
		System.out.print("First bits of first frame: ");
		System.out.println(Arrays.toString(MP3Frame.toBits(fileData, index, 4)));
		
		MP3Frame first = new MP3Frame(fileData, index);
		this.sampleRate = first.getSampleRate(); //assume constant sample rate
		index += first.getSize();
		
		List<MP3Frame> frames = new LinkedList<>();
		frames.add(first);
		//load all the frames
		while(index < fileData.length){
			MP3Frame temp = new MP3Frame(fileData, index);
			frames.add(temp);
			index += temp.getSize();	
		}
		System.out.println("Number of frames: " + frames.size());
		
		int totalNumberSamples = frames.size() * 1152; //1152 samples / frame
		
		if(first.getNumChannels() == ChannelMode.MONO){
			this.numChannels = 1;
			this.data = new byte[totalNumberSamples][2];
		} else {
			this.numChannels = 2;
			this.data = new byte[totalNumberSamples][4];
		}
		
		//use 8 * channels bits per sample (1 byte for each sample on each channel)
		this.player = new SoundPlayer(data, this.sampleRate, this.numChannels * 8, 
				this.numChannels, false, this);
		
		//the frames are all loaded
		Thread t = new Thread(() -> {
			int offset = 0;
			for(MP3Frame frame : frames){
				frame.loadData(data, offset);
				offset += 1152; //1152 samples per frame
			}
			
		});
		//don't want to halt the program from stopping while loading the frames.
		t.setDaemon(true);
		t.start();
		
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