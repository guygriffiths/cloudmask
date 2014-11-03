/*******************************************************************************
 * Copyright (c) 2014 The University of Reading
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package uk.ac.rdg.resc.cloudmask;

import java.awt.image.BufferedImage;
import java.io.File;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import javax.imageio.ImageIO;

public class CloudMask extends Application {

    public static void main(String[] args) {
        launch(args);
    }
    
    double offsetX = 0;
    double offsetY = 0;
    double zoom = 1.0;

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Cloud Masker");

        GridPane grid = new GridPane();

        BufferedImage bufferedImage = ImageIO.read(new File("/home/guy/Pictures/bluetit-512.jpg"));
//        BufferedImage bufferedImage = ImageIO.read(new File("/home/guy/Pictures/check.png"));
        Image image = SwingFXUtils.toFXImage(bufferedImage, null);
        
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(false);
        imageView.setViewport(new Rectangle2D(0, 0, image.getWidth(), image.getHeight()));
        imageView.setImage(image);
        
        Button test = new Button("Button");
        Button test2 = new Button("Button");
        grid.add(imageView, 0, 0);
        grid.add(test, 0, 1);
        grid.add(test2, 1, 0);
        
        ZoomableImageView zoomView = new ZoomableImageView(400, 400, new TestImageGenerator());
        grid.add(zoomView, 1, 1);
        
        imageView.setFitHeight(200);
        imageView.setFitWidth(200);
        
        imageView.setOnScroll(new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {
                zoom += event.getDeltaY()/400.0;
                if(zoom < 1.0) {
                    zoom = 1.0;
                }
                
                imageView.setViewport(new Rectangle2D(offsetX, offsetY, 512/zoom, 512/zoom));
            }
        });
        
        imageView.setOnZoom(new EventHandler<ZoomEvent>() {
            @Override
            public void handle(ZoomEvent event) {
                System.out.println("zoomed "+event.getTotalZoomFactor());
            }
        });
        
        imageView.setOnMousePressed(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                System.out.println("pressed");
                lastDragX = event.getX();
                lastDragY = event.getY();
            }
        });
        imageView.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                offsetX -= (event.getX() - lastDragX)/zoom;
                offsetY -= (event.getY() - lastDragY)/zoom;
                lastDragX = event.getX();
                lastDragY = event.getY();
                imageView.setViewport(new Rectangle2D(offsetX, offsetY, 512/zoom, 512/zoom));
            }
        });
//        imageView.addEventHandler(MouseEvent.MOUSE_DRAGGED, new EventHandler<MouseEvent>() {
//            @Override
//            public void handle(MouseEvent event) {
//                System.out.println(event.getX());
//                offsetX += event.getX();
//                offsetY += event.getY();
//                imageView.setViewport(new Rectangle2D(offsetX, offsetY, offsetX + 512/zoom, offsetY + 512/zoom));
//            }
//        });

        primaryStage.setScene(new Scene(grid, 300, 250));
        primaryStage.show();
    }
    private double lastDragX;
    private double lastDragY;

}
