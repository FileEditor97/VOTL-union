package union.utils.imagegen.renders;

import org.jetbrains.annotations.Nullable;
import union.utils.exception.RenderNotReadyYetException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@SuppressWarnings("unused")
public abstract class Renderer {

	/**
	 * Checks if the render is ready to be used, if the {@link #render()} or
	 * {@link #renderToBytes()} method is called while this returns false,
	 * the {@link RenderNotReadyYetException} will be thrown, preventing
	 * the render from running.
	 *
	 * @return <code>True</code> if the rendered is ready, <code>False</code> otherwise.
	 */
	public abstract boolean canRender();

	/**
	 * Handles the rendering process.
	 *
	 * @return The generated image as a buffered image object,
	 * or <code>NULL</code> if something went wrong.
	 * @throws IOException Thrown when underlying render throws an IOException
	 */
	@Nullable
	protected abstract BufferedImage handleRender() throws IOException;

	/**
	 * Render the image to build the buffered image object.
	 *
	 * @return The generated image as a buffered image object,
	 * or <code>NULL</code> if something went wrong.
	 * @throws IOException Thrown when underlying render throws an IOException.
	 */
	@Nullable
	public BufferedImage render() throws IOException {
		if (!canRender()) {
			throw new RenderNotReadyYetException("One or more required arguments for the renderer have not been setup yet.");
		}

		return handleRender();
	}

	/**
	 * Renders the image to build the buffered image object,
	 * then converts it to a byte stream.
	 *
	 * @return The generated image as an array of bytes, or <code>NULL</code>.
	 * @throws IOException Thrown when underlying render throws an IOException.
	 */
	@Nullable
	public byte[] renderToBytes() throws IOException {
		if (!canRender()) {
			throw new RenderNotReadyYetException("One or more required arguments for the renderer have not been setup yet.");
		}

		final BufferedImage bufferedImage = handleRender();
		if (bufferedImage == null) {
			return null;
		}

		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();

		ImageIO.write(bufferedImage, "png", byteStream);
		byteStream.flush();

		byte[] bytes = byteStream.toByteArray();
		byteStream.close();

		return bytes;
	}

	/**
	 * Resizes the given buffered image to the given height and width,
	 * scaling it using a smooth filter to avoid distorting the
	 * image as much as it is feasible.
	 *
	 * @param image  The image that should be resized.
	 * @param height The height that the image should be.
	 * @param width  The width that the image should be.
	 * @return The resized image.
	 */
	protected final BufferedImage resize(BufferedImage image, int height, int width) {
		Image scaledInstance = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
		BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		Graphics2D g2d = resized.createGraphics();

		g2d.drawImage(scaledInstance, 0, 0, null);
		g2d.dispose();

		return resized;
	}

	protected final BufferedImage resizedCircle(BufferedImage image, int size) {
		return resizedRounded(image, size, size, size);
	}

	/**
	 * Resizes and rounds the given buffered image to
	 * the given height and width.
	 *
	 * @param image  The image that should be resized.
	 * @param height The height that the image should be.
	 * @param width  The width that the image should be.
	 * @return The resized and rounded image.
	 */
	protected final BufferedImage resizedRounded(BufferedImage image, int height, int width, int radius) {
		Image scaledInstance = image.getScaledInstance(width, height, Image.SCALE_SMOOTH);

		BufferedImage rounded = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = rounded.createGraphics();

		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		RoundRectangle2D roundRectangle = new RoundRectangle2D.Float(0, 0, width, height, radius, radius);
		g2d.setClip(roundRectangle);

		g2d.drawImage(scaledInstance, 0, 0, null);
		g2d.dispose();

		return rounded;
	}

	/**
	 * Creates an sRGB color with the specified red, green,
	 * and blue values with in the range (0 - 255).
	 *
	 * @param red   The red component.
	 * @param green The green component
	 * @param blue  The blue component
	 * @return The color with the given values.
	 */
	protected final Color getColor(float red, float green, float blue) {
		return new Color(red / 255F, green / 255F, blue / 255F, 1F);
	}

	/**
	 * Creates an sRGBA color with the specified red, green,
	 * blue, and alpha values with in the range (0 - 255).
	 * The alpha should be in the rage of 0 and 100.
	 *
	 * @param red   The red component.
	 * @param green The green component.
	 * @param blue  The blue component.
	 * @param alpha The alpha component.
	 * @return The color with the given values.
	 */
	protected final Color getColor(float red, float green, float blue, float alpha) {
		return new Color(red / 255F, green / 255F, blue / 255F, alpha / 100F);
	}

}
