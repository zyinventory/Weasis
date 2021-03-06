/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec.display;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.image.DataBuffer;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.util.HashMap;

import javax.media.jai.PlanarImage;
import javax.media.jai.ROIShape;
import javax.media.jai.TiledImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AbstractOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.MergeImgOp;
import org.weasis.core.api.image.op.ShutterDescriptor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;

public class ShutterOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShutterOp.class);

    public static final String OP_NAME = ActionW.IMAGE_SHUTTER.getTitle();

    /**
     * Set whether the shutter is applied (Required parameter).
     * 
     * Boolean value.
     */
    public static final String P_SHOW = "show"; //$NON-NLS-1$
    public static final String P_SHAPE = "shape"; //$NON-NLS-1$
    public static final String P_RGB_COLOR = "rgb.color"; //$NON-NLS-1$
    public static final String P_PS_VALUE = "ps.value"; //$NON-NLS-1$

    public ShutterOp() {
        setName(OP_NAME);
    }

    @Override
    public void handleImageOpEvent(ImageOpEvent event) {
        OpEvent type = event.getEventType();
        if (OpEvent.ImageChange.equals(type) || OpEvent.ResetDisplay.equals(type)) {
            ImageElement img = event.getImage();
            // If no image, reset the shutter
            boolean noMedia = img == null;
            setParam(P_SHAPE, noMedia ? null : img.getTagValue(TagW.ShutterFinalShape));
            setParam(P_PS_VALUE, noMedia ? null : img.getTagValue(TagW.ShutterPSValue));
            setParam(P_RGB_COLOR, noMedia ? null : img.getTagValue(TagW.ShutterRGBColor));
        } else if (OpEvent.ApplyPR.equals(type)) {
            HashMap<String, Object> p = event.getParams();
            if (p != null) {
                Object prReader = p.get(ActionW.PR_STATE.cmd());
                PRSpecialElement pr =
                    (prReader instanceof PresentationStateReader) ? ((PresentationStateReader) prReader).getDicom()
                        : null;
                setParam(P_SHAPE, pr == null ? null : pr.getTagValue(TagW.ShutterFinalShape));
                setParam(P_PS_VALUE, pr == null ? null : pr.getTagValue(TagW.ShutterPSValue));
                setParam(P_RGB_COLOR, pr == null ? null : pr.getTagValue(TagW.ShutterRGBColor));

                // if (area != null) {
                // Area shape = (Area) actionsInView.get(TagW.ShutterFinalShape.getName());
                // if (shape != null) {
                // Area trArea = new Area(shape);
                // trArea.transform(AffineTransform.getTranslateInstance(-area.getX(), -area.getY()));
                // actionsInView.put(TagW.ShutterFinalShape.getName(), trArea);
                // }
                // }
            }
        }
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(INPUT_IMG);
        RenderedImage result = source;

        Boolean shutter = (Boolean) params.get(P_SHOW);
        Area area = (Area) params.get(P_SHAPE);

        if (shutter == null) {
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", OP_NAME); //$NON-NLS-1$
        } else if (shutter && area != null) {
            Byte[] color = getShutterColor();
            if (isBlack(color)) {
                result = ShutterDescriptor.create(source, new ROIShape(area), getShutterColor(), null);
            } else {
                result =
                    MergeImgOp.combineTwoImages(source,
                        ImageFiler.getEmptyImage(color, source.getWidth(), source.getHeight()),
                        getAsImage(area, source));
            }
        }

        params.put(OUTPUT_IMG, result);
    }

    private boolean isBlack(Byte[] color) {
        for (Byte i : color) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    private Byte[] getShutterColor() {
        Color color = (Color) params.get(P_RGB_COLOR);
        if (color == null) {
            /*
             * A single gray unsigned value used to replace those parts of the image occluded by the shutter, when
             * rendered on a monochrome display. The units are specified in P-Values, from a minimum of 0000H (black) up
             * to a maximum of FFFFH (white).
             */
            Integer val = (Integer) params.get(P_PS_VALUE);
            return val == null ? new Byte[] { 0 } : new Byte[] { (byte) (val >> 8) };
        } else {
            Byte[] bandValues = { (byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue() };
            return bandValues;
        }
    }

    private PlanarImage getAsImage(Area shape, RenderedImage source) {
        SampleModel sm =
            new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, source.getWidth(), source.getHeight(), 1);
        TiledImage ti =
            new TiledImage(source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight(),
                source.getTileGridXOffset(), source.getTileGridYOffset(), sm, PlanarImage.createColorModel(sm));
        Graphics2D g2d = ti.createGraphics();
        // Write the Shape into the TiledImageGraphics.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.fill(shape);
        g2d.dispose();
        return ti;
    }
}
