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

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

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
        
        LinkedZoomableImageView mandelView = new LinkedZoomableImageView(400, 400, new MandelbrotImageGenerator());
        LinkedZoomableImageView mandelView2 = new LinkedZoomableImageView(300, 300, new MandelbrotImageGenerator("div-PiYG"));
        LinkedZoomableImageView juliaView = new LinkedZoomableImageView(400, 400, new JuliaImageGenerator());
        
        mandelView.addLinkedView(juliaView);
        mandelView.addLinkedView(mandelView2);
        
        grid.add(mandelView, 0, 0);
        grid.add(mandelView2, 0, 1);
        grid.add(juliaView, 1, 0);

        primaryStage.setScene(new Scene(grid, 300, 250));
        primaryStage.show();
    }
}
