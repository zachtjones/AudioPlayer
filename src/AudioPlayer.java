import java.io.File;

import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

public class AudioPlayer extends Application implements InvalidationListener{

	/** This is the model for the program*/
	private Player p;
	/** Holds a reference to the primary stage for this application */
	private Stage primaryStage;
	
	public static void main(String[] args) {
		//launch the program
		Application.launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.primaryStage = primaryStage;
		
		Image playImg = new Image("play.png");
		Image stopImg = new Image("stop.png");
		Image pauseImg = new Image("pause.png");
		
		
		VBox page = new VBox();
		page.setPadding(new Insets(20));
		page.setSpacing(20);
		
		//the open button
		Button open = new Button("Open a file");
		open.setOnAction(event -> {
			FileChooser fc = new FileChooser();
			fc.setTitle("Select the audio file");
			fc.getExtensionFilters().add(new ExtensionFilter("Audio files (*.aac, *.aiff, *.flac, *.m4a, *.mp3, *.ogg, *.wav)", 
					"*.aac", "*.aiff", "*.flac", "*.m4a", "*.mp3", "*.ogg", "*.wav"));
			File f = fc.showOpenDialog(primaryStage);
			if(f == null){ return; } //no file selected
			
			p.openFile(f);
		});
		page.getChildren().add(open);
		
		//the progress bar
		ProgressBar pb = new ProgressBar();
		pb.setProgress(0);
		page.getChildren().add(pb);
		
		//the buttons
		HBox buttons = new HBox();
		
		//play/pause button
		Button playPause = new Button();
		ImageView playPauseImgView = new ImageView(playImg);
		playPauseImgView.setFitWidth(40);
		playPauseImgView.setFitHeight(40);
		playPause.setGraphic(playPauseImgView);
		buttons.getChildren().add(playPause);
		
		//stop button
		Button stop = new Button();
		ImageView stopImgView = new ImageView(stopImg);
		stopImgView.setFitWidth(40);
		stopImgView.setFitHeight(40);
		stop.setGraphic(stopImgView);
		buttons.getChildren().add(stop);
		
		page.getChildren().add(buttons);
		
		//construct the model
		p = new Player();
		p.addListener(this);
		
		Scene scene = new Scene(page);
		primaryStage.setScene(scene);
		primaryStage.setWidth(600);
		primaryStage.setHeight(400);
		primaryStage.setTitle("Audio Player");
		primaryStage.show();
		
	}

	@Override
	public void invalidated(Observable observable) {
		//set the title to the file loaded, if there is one
		this.primaryStage.setTitle("Audio Player " + (p.getFilename() == null ? "" : " - " + p.getFilename()));
		// TODO Auto-generated method stub
		
	}

}
