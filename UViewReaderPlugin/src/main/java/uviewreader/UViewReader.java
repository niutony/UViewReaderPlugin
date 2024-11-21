/**
 * Scifio-UView plugin. This plugin reads single images from the UKSOFT2000 format. This format is used by the 
 * Elmitec camera adquisition program for their LEEM/PEEM line of instruments.
 * 
 * It is a simple unsigned 16bit binary dump preceeded by a header with some experimental parameters and the size.
 * For the record, it is the same format originally used in a Transputer electronics control unit for Scanning Tunneling
 * Microcopy, from Uwe Knipping (who then moved to Elmitec).
 *
 * @author Juan de la Figuera created the first version and Yuran Niu simplified and upgraded the codes to fit the latest fiji
 */
package uviewreader;

import io.scif.AbstractChecker;
import io.scif.AbstractFormat;
import io.scif.AbstractMetadata;
import io.scif.AbstractParser;
import io.scif.ByteArrayPlane;
import io.scif.ByteArrayReader;
import io.scif.Format;
import io.scif.FormatException;
import io.scif.ImageMetadata;
import io.scif.config.SCIFIOConfig;
import io.scif.util.FormatTools;

import java.io.IOException;

import net.imagej.axis.Axes;
import net.imglib2.Interval;

import org.scijava.Priority;
import org.scijava.io.handle.DataHandle;
import org.scijava.io.handle.DataHandle.ByteOrder;
import org.scijava.io.location.Location;
import org.scijava.plugin.Plugin;

@Plugin(type = Format.class, name = "UView Format", priority = Priority.NORMAL)
public class UViewReader extends AbstractFormat {

    // --- Nested Classes ---

    public static class Metadata extends AbstractMetadata {

        private static final long serialVersionUID = 1L;

        private int offset;

        @Override
        public void populateImageMetadata() {
            ImageMetadata iMeta = get(0);
            iMeta.setBitsPerPixel(16);
            iMeta.setLittleEndian(true);
            iMeta.setPixelType(FormatTools.UINT16);
            iMeta.setFalseColor(false);
            iMeta.setMetadataComplete(true);
            iMeta.setThumbnail(false);
            iMeta.setOrderCertain(true);
        }

        public int getOffset() {
            return offset;
        }

        public void setOffset(int offset) {
            this.offset = offset;
        }
    }

    public static class Checker extends AbstractChecker {

        private static final String UVIEW_MAGIC_STRING = "UKSOFT2001";

        @Override
        public boolean isFormat(DataHandle<Location> handle) throws IOException {
            final int blockLen = UVIEW_MAGIC_STRING.length();
            if (!FormatTools.validStream(handle, blockLen, false))
                return false;
            handle.seek(0);
            return handle.readString(blockLen).startsWith(UVIEW_MAGIC_STRING);
        }

        @Override
        public boolean suffixNecessary() {
            return false;
        }

        @Override
        public boolean suffixSufficient() {
            return false;
        }
    }

    public static class Parser extends AbstractParser<Metadata> {

        @Override
        protected void typedParse(DataHandle<Location> handle, Metadata meta, SCIFIOConfig config)
                throws IOException, FormatException {
            meta.createImageMetadata(1);
            final ImageMetadata iMeta = meta.get(0);
            handle.setOrder(ByteOrder.LITTLE_ENDIAN);

            long fileLength = handle.length();

            // Read width, height, and number of images
            handle.seek(40);
            int UKFH_width = handle.readUnsignedShort();
            int UKFH_height = handle.readUnsignedShort();
            int UKFH_nimages = handle.readUnsignedShort();

            iMeta.addAxis(Axes.X, UKFH_width);
            iMeta.addAxis(Axes.Y, UKFH_height);

            meta.setOffset((int) fileLength - 2 * UKFH_width * UKFH_height);

            // Populate other ImageMetadata fields
            iMeta.setPixelType(FormatTools.UINT16);
            iMeta.setBitsPerPixel(16);
            iMeta.setLittleEndian(true);
            iMeta.setMetadataComplete(true);
        }
    }

    public static class Reader extends ByteArrayReader<Metadata> {

        @Override
        public ByteArrayPlane openPlane(int imageIndex, long planeIndex, ByteArrayPlane plane, Interval bounds,
                SCIFIOConfig config) throws FormatException, IOException {

            final Metadata meta = getMetadata();
            final byte[] buf = plane.getData();

            FormatTools.checkPlaneForReading(meta, imageIndex, planeIndex, buf.length, bounds);

            int width = (int) meta.get(0).getAxisLength(Axes.X);
            int height = (int) meta.get(0).getAxisLength(Axes.Y);
            DataHandle<Location> handle = getHandle();
            handle.setOrder(ByteOrder.LITTLE_ENDIAN);
            handle.seek(meta.getOffset());

            // Read and flip the image vertically
            for (int i = 0; i < height; i++) {
                int destPos = (height - 1 - i) * width * 2; // 2 bytes per pixel for UINT16
                handle.read(buf, destPos, width * 2);
            }

            return plane;
        }

        @Override
        protected String[] createDomainArray() {
            return new String[] { FormatTools.UNKNOWN_DOMAIN };
        }
    }

    @Override
    protected String[] makeSuffixArray() {
        return new String[] { "dat" };
    }

    @Override
    public String getFormatName() {
        return "UView Format";
    }

    @Override
    public Metadata createMetadata() {
        return new Metadata();
    }
}