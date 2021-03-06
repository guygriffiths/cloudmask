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

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class UndoRedoManagerTest {

    private UndoRedoManager<String> urm;

    @Before
    public void setUp() {
        urm = new UndoRedoManager<>("START");
    }

    @Test
    public void testUndoRedo() {
        /*
         * Undoing an empty queue should be null
         */
        assertNull(urm.undo());
        /*
         * Redoing an empty queue should be null
         */
        assertNull(urm.redo());
        
        /*
         * Add an operation.
         */
        urm.setCurrentState("1");
        assertEquals("START", urm.undo());
        assertNull(urm.undo());
        assertNull(urm.undo());
        assertEquals("1", urm.redo());
        assertEquals("START", urm.undo());
        assertEquals("1", urm.redo());
        assertEquals("START", urm.undo());
        assertEquals("1", urm.redo());
        assertNull(urm.redo());
        assertNull(urm.redo());
        
        /*
         * Add another operation.
         */
        urm.setCurrentState("2");
        assertEquals("1", urm.undo());
        assertEquals("START", urm.undo());
        assertEquals("1", urm.redo());
        assertEquals("2", urm.redo());
        assertNull(urm.redo());
        assertNull(urm.redo());
        assertEquals("1", urm.undo());
        assertEquals("START", urm.undo());
        assertEquals("1", urm.redo());
        urm.setCurrentState("two");
        assertEquals("1", urm.undo());
        assertEquals("START", urm.undo());
        assertNull(urm.undo());
        assertNull(urm.undo());
        assertNull(urm.undo());
        assertEquals("1", urm.redo());
        assertEquals("two", urm.redo());
        assertNull(urm.redo());
        urm.setCurrentState("3");
        urm.setCurrentState("4");
        assertEquals("3", urm.undo());
        assertEquals("two", urm.undo());
        assertEquals("1", urm.undo());
        assertEquals("START", urm.undo());
        
        assertEquals("1", urm.redo());
        assertEquals("two", urm.redo());
        assertEquals("3", urm.redo());
        assertEquals("4", urm.redo());
        assertEquals("3", urm.undo());
        assertEquals("two", urm.undo());
        urm.setCurrentState("three");
        assertNull(urm.redo());
        assertNull(urm.redo());
        assertEquals("two", urm.undo());
        assertEquals("1", urm.undo());
        assertEquals("START", urm.undo());
        assertNull(urm.undo());
    }

}
