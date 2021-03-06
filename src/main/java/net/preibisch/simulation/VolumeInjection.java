package net.preibisch.simulation;

import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class VolumeInjection
{
	final RandomAccessibleInterval< FloatType > image, weight;
	final RandomAccessible< FloatType > infinite, infWeight;
	final int numDimensions;
	final int[] size;
	final double[] two_sq_sigma;
	final double sumWeights;
	final int numPixels;

	public VolumeInjection(
			final RandomAccessibleInterval< FloatType > image,
			final RandomAccessibleInterval< FloatType > weight,
			final double[] sigma )
	{
		this.numDimensions = sigma.length;
		this.image = image;
		this.weight = weight;
		this.infinite = Views.extendZero( image );
		this.infWeight = Views.extendZero( weight );

		this.size = new int[ numDimensions ];

		this.two_sq_sigma = new double[ numDimensions ];

		for ( int d = 0; d < numDimensions; ++d )
		{
			if ( sigma[ d ] == 0 )
			{
				size[ d ] = 1;
				two_sq_sigma[ d ] = 1;
			}
			else
			{
				size[ d ] = Util.getSuggestedKernelDiameter( sigma[ d ] );
				two_sq_sigma[ d ] = 2 * sigma[ d ] * sigma[ d ];
			}
		}

		double sumWeights = 0;
		int numPixels = 0;

		final Cursor< FloatType > cursor = getCursor( new double[] { 0.0, 0.0, 0.0 } );

		while ( cursor.hasNext() )
		{
			cursor.fwd();

			double value = 1;
			
			for ( int d = 0; d < numDimensions; ++d )
				value *= getGaussValue( 0, cursor.getLongPosition( d ), two_sq_sigma[ d ] );

			sumWeights += value;
			++numPixels;
		}

		this.sumWeights = sumWeights;
		this.numPixels = numPixels;
	}

	public int[] getSize() { return size; }

	public RandomAccessibleInterval< FloatType > getImage() { return image; }
	public RandomAccessibleInterval< FloatType > getWeight() { return weight; }

	public Img< FloatType > normalize()
	{
		return normalize( image, weight );
	}

	public static Img< FloatType > normalize( final RandomAccessibleInterval< FloatType > image, final RandomAccessibleInterval< FloatType > weight)
	{
		final Img< FloatType > normed = ArrayImgs.floats( image.dimension( 0 ), image.dimension( 1 ), image.dimension( 2 ) );

		normalize( normed, image, weight );

		return normed;
	}

	public static void normalize( final RandomAccessibleInterval< FloatType > normed, final RandomAccessibleInterval< FloatType > image, final RandomAccessibleInterval< FloatType > weight )
	{
		final Cursor< FloatType > c = Views.iterable( normed ).localizingCursor();
		final RandomAccess< FloatType > rI = image.randomAccess();
		final RandomAccess< FloatType > rW = weight.randomAccess();

		while ( c.hasNext() )
		{
			final FloatType pixel = c.next();
			rI.setPosition( c );
			rW.setPosition( c );

			final float w = rW.get().get();
			final float v = rI.get().get();

			if ( w > 1.0f )
				pixel.set( v / w );
			else
				pixel.set( v );
		}
	}

	public double getSumWeights()
	{
		return sumWeights;
	}

	public int getNumPixels()
	{
		return numPixels;
	}

	public final Cursor< FloatType > getCursor( final double[] location )
	{
		final long[] min = new long[ numDimensions ];
		final long[] max = new long[ numDimensions ];

		for ( int d = 0; d < numDimensions; ++d )
		{
			min[ d ] = Math.round( location[ d ] ) - size[ d ]/2;
			max[ d ] = min[ d ] + size[ d ] - 1;
		}

		final RandomAccessibleInterval< FloatType > interval = Views.interval( infinite, min, max );
		return Views.iterable( interval ).localizingCursor();
	}

	private static final double getGaussValue( final double gaussLocation, final long cursorLocation, final double two_sq_sigma )
	{
		final double x = gaussLocation - cursorLocation;
		return Math.exp( -(x * x) / two_sq_sigma );
	}

	public void addNormalizedGaussian(
			final double intensity,
			final double[] location )
	{
		addGaussian( intensity / sumWeights, location );
	}

	public void addGaussian(
			final double intensity,
			final double[] location )
	{
		final Cursor< FloatType > cursor = getCursor( location );
		final RandomAccess< FloatType > ra = infWeight.randomAccess();

		while ( cursor.hasNext() )
		{
			final FloatType pixel = cursor.next();

			ra.setPosition( cursor );
			final FloatType weight = ra.get();

			double value = 1;

			for ( int d = 0; d < numDimensions; ++d )
				value *= getGaussValue( location[ d ], cursor.getLongPosition( d ), two_sq_sigma[ d ] );

			pixel.set( pixel.get() + (float)( value * intensity ) );
			weight.set( weight.get() + (float)value );
		}
	}

	public Img< FloatType > project()
	{
		final Img< FloatType > proj = ArrayImgs.floats( image.dimension( 0 ), image.dimension( 1 ) );

		final RandomAccess< FloatType > ra = image.randomAccess();
		final RandomAccess< FloatType > raW = weight.randomAccess();
		final Cursor< FloatType > c = proj.localizingCursor();

		while ( c.hasNext() )
		{
			c.fwd();
			ra.setPosition( c.getIntPosition( 0 ), 0 );
			ra.setPosition( c.getIntPosition( 1 ), 1 );

			double sum = 0;
			double count = 0;

			for ( int z = 0; z < image.dimension( 2 ); ++z )
			{
				ra.setPosition( z, 2 );
				raW.setPosition( ra );

				if ( ra.get().get() > 0 )
				{
					sum += ra.get().get() * raW.get().get();
					count += raW.get().get();
				}
			}

			c.get().setReal( sum / count );
		}

		return proj;
	}
}
