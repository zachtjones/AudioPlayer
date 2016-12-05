package model;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import javafx.application.Platform;
import runner.AudioRun;

public class SoundPlayer implements AudioRun {
	/** If this is closed*/
	private boolean isClosed;
	/** The frames of audio, each frame is a byte array */
	private byte[][] frames;
	/** the number of samples per second */
	private float sampleRate;
	/** the cursor location, the current frame */
	private int cursor;
	/** if this is paused */
	private boolean isPaused;
	/** The *.* file runner*/
	private AudioRun runner;
	
	
	/**
	 * Constructor for a device to play the raw sound data.
	 * @param frames The byte 2-d array that each frame is a byte 1-d array.
	 * @param sampleRate The number of samples per second as an int.
	 * @param sampleBitSize The number of bits per sample as an int.
	 * @param numChannels The number of channels of sound (1 is mono, 2 stereo, ...)
	 * @param bigEndian Whether the data is big endian (true), or little endian (false)
	 * @param runner The runner that constructed this object
	 */
	public SoundPlayer(byte[][] frames, float sampleRate, int sampleBitSize, int numChannels, boolean bigEndian, AudioRun runner){
		this.runner = runner;
		this.isClosed = false;
		this.frames = frames;
		this.cursor = 0;
		this.isPaused = true;
		this.sampleRate = sampleRate;
		
		Thread t = new Thread(() -> {
			try {
				AudioFormat af = new AudioFormat(sampleRate, sampleBitSize, numChannels, true, bigEndian);
				SourceDataLine sdl = AudioSystem.getSourceDataLine(af);
				sdl.open();
				sdl.start();
				while(!isClosed){
					while(this.isPaused && !isClosed){
						sleep(50);
					}
					
					//write the data
					//blocks for 1/sampleRate of a second
					sdl.write(frames[cursor], 0, frames[cursor].length); 
					this.cursor ++;
					
					//update UI if needed 1 time every second
					if(this.cursor % (int)this.sampleRate == 0){
						Platform.runLater(() -> {
							this.stateChanged();
						});
					}
					
					//update UI if at the end, only once
					if(this.cursor >= frames.length){
						Platform.runLater(() -> {
							this.stateChanged();
						});
					}
					
					//at end, sleep until closed, or changed cursor position.
					while(this.cursor >= frames.length && !isClosed){
						sleep(50);
					}
				}
				sdl.drain();
				sdl.stop();
				System.out.println("Sound-playing thread finished normally.");
			} catch(LineUnavailableException e){
				System.out.println(e);
			}
		});
		t.setName("Sound-playing thread");
		t.start();
	}
	
	
	@Override
	public void play() {
		//reset to beginning if at end
		if(this.cursor >= frames.length){
			this.cursor = 0; 
		} else {
			this.isPaused = false;
		}
		this.stateChanged();
	}

	@Override
	public void pause() {
		//pause this
		this.isPaused = true;
		this.stateChanged();
	}

	@Override
	public void stop() {
		//pause and move to beginning
		this.isPaused = true;
		this.cursor = 0;
		this.stateChanged();
	}

	@Override
	public void close() {
		//mark as closed, terminating the loop in the secondary thread.
		this.isClosed = true;
		this.stateChanged();
	}

	@Override
	public int getTime() {
		//the cursor / the total number of frames
		return (int) (this.cursor / this.sampleRate);
	}

	@Override
	public int getLength() {
		//the total number of frames / frames per second
		return (int) (this.frames.length / this.sampleRate);
	}

	/** Calls Thread.sleep(mills) in a try.. catch block for convenience */
	private void sleep(int mills){
		try {
			Thread.sleep(mills);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}


	@Override
	public void stateChanged() {
		//call the state changed of the runner
		runner.stateChanged();
	}


	@Override
	public boolean isAtEnd() {
		//return true if this is at the end of the file
		return this.cursor == this.frames.length;
	}
}
