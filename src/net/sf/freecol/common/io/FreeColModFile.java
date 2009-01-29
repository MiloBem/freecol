/**
 *  Copyright (C) 2002-2008  The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.common.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import net.sf.freecol.FreeCol;
import net.sf.freecol.client.gui.i18n.Messages;


/**
 * A modification.
 */
public class FreeColModFile extends FreeColDataFile {
    
    private static final String SPECIFICATION_FILE = "specification.xml";
    private static final String MOD_INFO_FILE = "mod.xml";
    private static final String[] FILE_ENDINGS = new String[] {".fmd", ".zip"};

    private String id;
    
    /**
     * Opens the given file for reading.
     * 
     * @param id The id of the mod to load.
     * @throws IOException if thrown while opening the file.
     */
    public FreeColModFile(final String id) throws IOException {
        this(id, new File(FreeCol.getModsDirectory(), id));
    }
    
    /**
     * Opens the given file for reading.
     *
     * @param id The id of the mod.
     * @param file The file to be read.
     * @throws IOException if thrown while opening the file.
     */
    protected FreeColModFile(final String id, final File file) throws IOException {
        super(file);
        
        this.id = id;
    }
    
    /**
     * Gets the input stream to the specification.
     * 
     * @return An <code>InputStream</code> to the file
     *      "specification.xml" within this data file.
     * @throws IOException if thrown while opening the
     *      input stream.
     */
    public InputStream getSpecificationInputStream() throws IOException {
        return getInputStream(SPECIFICATION_FILE);
    }
    
    /**
     * Returns an object representing this mod.
     * 
     * @return The meta information for this mod file.
     * @throws IOException if thrown while reading the
     *      "mod.xml" file.
     */
    public ModInfo getModInfo() throws IOException {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        XMLStreamReader in = null;
        try {
            in = xif.createXMLStreamReader(getModInfoInputStream());
            in.nextTag();
            final ModInfo mi = new ModInfo(id, in);
            return mi;
        } catch (XMLStreamException e) {
            final IOException e2 = new IOException("XMLStreamException.");
            e2.initCause(e);
            throw e2;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {}
        }
    }
    
    /**
     * Gets the input stream to the mod meta file.
     * 
     * @return An <code>InputStream</code> to the file
     *      "mod.xml" within this data file.
     * @throws IOException if thrown while opening the
     *      input stream.
     */
    private InputStream getModInfoInputStream() throws IOException {
        return getInputStream(MOD_INFO_FILE);
    }
    
    /**
     * File endings that are supported for this type of data file.
     * @return An array of: ".fmd" and ".zip".
     */
    protected String[] getFileEndings() {
        return FILE_ENDINGS;
    }
    
    public static class ModInfo {

        private final String id;
        private final String parent;
        
        /**
         * Initiates a new <code>ModInfo</code> from XML.
         *
         * @param id The mod to be loaded.
         * @param in The input stream containing the XML.
         * @throws XMLStreamException if a problem was encountered
         *      during parsing.
         */
        protected ModInfo(final String id, XMLStreamReader in) throws XMLStreamException {
            this.id = id;
            this.parent = in.getAttributeValue(null, "parent");
        }
        
        /**
         * Returns the id of the mod.
         * @return The id.
         */
        public String getId() {
            return id;
        }
        
        /**
         * Gets the parent of the mod.
         * @return
         */
        public String getParent() {
            return parent;
        }
        
        /**
         * Gets the name of this mod.
         */
        public String getName() {
            // TODO: Get the text from the properties-file within the mod.
            return Messages.message(getId() + ".name");
        }
        
        /**
         * Gets a short description of this mod.
         */
        public String getShortDescription() {
            // TODO: Get the text from the properties-file within the mod.
            return Messages.message(getId() + ".shortDescription");
        }
    }
}
