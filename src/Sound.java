import java.util.HashSet;
import java.util.Iterator;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class Sound {
	
	/** The sample rate, in samples per second */
	private float sampleRate;
	/** The buffer that holds the data */
	private byte[] buf = new byte[1];
	/** The set of notes to play */
	private HashSet<Note> notes;
	
	private boolean shouldStop = false;
	
	private boolean stopWhenDone = false;
		
	public Sound(){
		this.sampleRate = 44100f;
		this.notes = new HashSet<>();
		Thread t = new Thread(() -> {
			try {
				AudioFormat af = new AudioFormat(sampleRate, 8, 1, true, false);
				SourceDataLine sdl = AudioSystem.getSourceDataLine( af );
				sdl.open();
				sdl.start();
				long i = 0;
				while(!shouldStop){
					i++;
					//get the amplitude of the sound to play at this instant
					double amplitudeSum = 0.0;
					int size = notes.size();
					if(size == 0 && stopWhenDone){
						break; //exit this, allowing proper closure
					}
					synchronized(notes){
						for(Iterator<Note> iterator = notes.iterator(); iterator.hasNext(); ){
							Note n = iterator.next();
							double angle = i / (sampleRate / n.getFreq()) * 2.0 * Math.PI;
							amplitudeSum += Math.sin(angle) * n.getVolume();
							n.descreaseCycle();
							//if the amount of time left is 0, then remove the item
							if(n.getNumCycles() == 0){
								iterator.remove();
							}
						}
					}
					
					amplitudeSum /= size;
					buf[0] = (byte) amplitudeSum;
					sdl.write(buf, 0, 1); //blocks for 1/sampleRate of a second
					
				}
				sdl.drain();
				sdl.stop();
				System.out.println("This thread finished normally.");
			} catch(LineUnavailableException e){
				System.out.println(e);
			}
		});
		t.setName("Sound thread");
		t.setDaemon(true);
		t.start();
		//start the thread
	}
	
	/**
	 * Adds a sound to be played for a duration at a volume.
	 * @param freq The frequency of sound, in Hz
	 * @param duration The number of seconds to play the sound.
	 * @param volume The volume to play the sound, 100 at full volume
	 */
	public void addSound(int freq, float duration, byte volume){
		Note n = new Note(freq, (long)(duration * sampleRate), volume);
		synchronized(this.notes){
			this.notes.add(n);
		}
		
	}
	
	/**
	 * Stops the Sound from running.
	 * Any further addSound calls after this method will do nothing.
	 * Make sure to call this method or closeWhenDone to dispose of this object properly, 
	 * otherwise the secondary thread that runs the sounds will not stop.
	 */
	public void close(){
		//set the flag to stop
		shouldStop = true;
	}
	
	/**
	 * Stops the Sound from running, once the queue is empty
	 * Any further addSound calls after this method will do nothing.
	 * Make sure to call this method or closeWhenDone to dispose of this object properly, 
	 * otherwise the secondary thread that runs the sounds will not stop.
	 */
	public void closeWhenDone(){
		this.stopWhenDone = true;
	}
}
