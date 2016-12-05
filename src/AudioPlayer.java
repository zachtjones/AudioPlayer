import java.io.File;
import java.io.IOException;

import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

public class AudioPlayer extends Application implements InvalidationListener{

	/** This is the model for the program*/
	private Player p;
	/** Holds a reference to the primary stage for this application */
	private Stage primaryStage;
	
	/** the button that is clicked to play or pause*/
	private Button btnPlayPause;
	/** the button that is clicked to stop the audio */
	private Button stop;
	/** The progressbar that shows how far through the song it is. */
	private ProgressBar pb;
	/** The play image */
	private Image playImg;
	/** The stop image */
	private Image stopImg;
	/** The pause image */
	private Image pauseImg;
	/** The label for the time in the audio file*/
	private Label lblTime;
	/** The label for the length of the audio file*/
	private Label lblLength;
	/** The label that shows the file info (sample rate & channels)*/
	private Label lblInfo;
	
	public static void main(String[] args) {
		//launch the program
		Application.launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.primaryStage = primaryStage;
		
		playImg = new Image("play.png");
		stopImg = new Image("stop.png");
		pauseImg = new Image("pause.png");
		
		VBox page = new VBox();
		page.setPadding(new Insets(20));
		page.setSpacing(20);
		page.setFillWidth(true);
		
		//the open button
		Button open = new Button("Open a file");
		open.setOnAction(event -> {
			FileChooser fc = new FileChooser();
			fc.setTitle("Select the audio file");
			fc.getExtensionFilters().add(new ExtensionFilter("Audio files (*.aac, *.aiff, *.flac, *.m4a, *.mp3, *.ogg, *.wav)", 
					"*.aac", "*.aiff", "*.flac", "*.m4a", "*.mp3", "*.ogg", "*.wav"));
			File f = fc.showOpenDialog(primaryStage);
			if(f == null){ return; } //no file selected
			
			try {
				p.openFile(f);
			} catch (IOException e) {
				Alert a = new Alert(AlertType.ERROR);
				a.setTitle("An error occured loading the file.");
				a.setContentText("Error message: " + e.getMessage());
				a.showAndWait();
			}
		});
		page.getChildren().add(open);
		
		//the row of 2 labels and the progress bar
		HBox progress = new HBox();
		
		lblTime = new Label("--:--");
		HBox.setHgrow(lblTime, Priority.NEVER);
		progress.getChildren().add(lblTime);
		
		//the progress bar
		pb = new ProgressBar();
		HBox.setHgrow(pb, Priority.ALWAYS);
		pb.setProgress(0);
		progress.getChildren().add(pb);
		
		lblLength = new Label("--:--");
		HBox.setHgrow(lblLength, Priority.NEVER);
		progress.getChildren().add(lblLength);
		
		page.getChildren().add(progress);
		
		//the buttons
		HBox buttons = new HBox();
		
		//play/pause button
		btnPlayPause = new Button();
		ImageView playPauseImgView = new ImageView(playImg);
		playPauseImgView.setFitWidth(40);
		playPauseImgView.setFitHeight(40);
		btnPlayPause.setGraphic(playPauseImgView);
		btnPlayPause.setOnAction(event -> {
			if(p.canPlay()){
				//button plays
				p.play();
				//button should show pause
				ImageView imgView = new ImageView(pauseImg);
				imgView.setFitWidth(40);
				imgView.setFitHeight(40);
				btnPlayPause.setGraphic(imgView);
			} else {
				//button pauses
				p.pause();
				//button should show play
				btnPlayPause.setGraphic(playPauseImgView);
			}
		});
		buttons.getChildren().add(btnPlayPause);
		
		//stop button
		stop = new Button();
		ImageView stopImgView = new ImageView(stopImg);
		stopImgView.setFitWidth(40);
		stopImgView.setFitHeight(40);
		stop.setGraphic(stopImgView);
		stop.setOnAction(event -> {
			p.stop();
		});
		buttons.getChildren().add(stop);
		
		page.getChildren().add(buttons);
		
		//add the info label 
		lblInfo = new Label();
		page.getChildren().add(lblInfo);
		
		//construct the model
		p = new Player();
		p.addListener(this);
		invalidated(p);
		
		Scene scene = new Scene(page);
		primaryStage.setScene(scene);
		primaryStage.setTitle("Audio Player");
		primaryStage.setResizable(false);
		primaryStage.setOnCloseRequest(event ->{
			//close the player when this closes
			p.close();
		});
		primaryStage.show();
		
	}

	@Override
	public void invalidated(Observable observable) {
		//set the title to the file loaded, if there is one
		this.primaryStage.setTitle("Audio Player " + (p.getFilename() == null ? "" : " - " + p.getFilename()));
		//play and pause buttons
		if(!p.canPause() && !p.canPlay()){
			btnPlayPause.setDisable(true);
		} else {
			btnPlayPause.setDisable(false);
			ImageView imgView;
			if(p.canPlay()){
				//play should show
				imgView = new ImageView(playImg);
			} else {
				//pause should show
				imgView = new ImageView(pauseImg);
			}
			imgView.setFitWidth(40);
			imgView.setFitHeight(40);
			btnPlayPause.setGraphic(imgView);
		}
		//stop button
		stop.setDisable(!p.canStop());

		//progressbar and labels
		int time = p.getTime();
		int length = p.getLength();
		if(time != -1 && length != -1){
			pb.setProgress((double)time / (double)length);
		}
		int seconds = time % 60;
		lblTime.setText(time == -1 ? "--:--" : "" + (time / 60) + ":" + 
				(seconds < 10 ? "0" + seconds : seconds));
		seconds = length % 60;
		lblLength.setText(length == -1 ? "--:--" : "" + (length / 60) + ":" + 
				(seconds < 10 ? "0" + seconds : seconds));
		//the info label
		lblInfo.setText(p.getInfo());
	}

}
