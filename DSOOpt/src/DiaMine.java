import java.io.*;
import java.text.*;
import java.util.*;


public class DiaMine {

	private static enum EventType {
		Prod, Weekly, Buy, Start, Finish
	}

	static DateFormat dfYYYY_MM_DD = new SimpleDateFormat( "YYYY-MM-dd");
	static DateFormat dfYYYY_MM_DD_HH_MM_SS = new SimpleDateFormat( "YYYY-MM-dd HH:mm:ss");

//	public static Calendar	getOffset( long offset) {
//		Calendar cal = GregorianCalendar.getInstance();
//		cal.setTimeInMillis( offset);
//		cal.add(  Calendar.YEAR, -1970);
//		return cal;
//	}
//
	public static String getOffsetAsString( long before, long after) {
		// Kalender bauen
		Calendar	cNow = new GregorianCalendar();
		cNow.setTimeInMillis( after);
		Calendar	cThen = new GregorianCalendar();
		cThen.setTimeInMillis( before);
		Calendar cBefore;
		Calendar cAfter;
		if ( before > after) {
			cBefore = ( Calendar) cNow.clone();
			cAfter = cThen;
		} else {
			cBefore = ( Calendar) cThen.clone();
			cAfter = cNow;
		}
		// diff ausrechnen
		Map<Integer, Long> diffMap = new HashMap<Integer, Long>();
		int[] calFields = { Calendar.YEAR, Calendar.MONTH, Calendar.DAY_OF_MONTH, Calendar.HOUR_OF_DAY, Calendar.MINUTE, Calendar.SECOND, Calendar.MILLISECOND};
		for ( int i = 0; i < calFields.length; i++) {
			int field = calFields[ i];
			long	d = computeDist( cAfter, cBefore, field);
			diffMap.put( field, d);
		}
		System.out.println( dfYYYY_MM_DD_HH_MM_SS.format( new Date( cBefore.getTimeInMillis())) + " -> " + dfYYYY_MM_DD_HH_MM_SS.format( new Date( cNow.getTimeInMillis())));
		final String result = String.format( "%dY %dM %dD %02d:%02d:%02d.%03d",
				diffMap.get( Calendar.YEAR), diffMap.get( Calendar.MONTH), diffMap.get( Calendar.DAY_OF_MONTH), diffMap.get( Calendar.HOUR_OF_DAY), diffMap.get( Calendar.MINUTE), diffMap.get( Calendar.SECOND), diffMap.get( Calendar.MILLISECOND));
		return result;
	}

	private static int computeDist( Calendar cAfter, Calendar cBefore, int field) {
		cBefore.setLenient( true);
		int	count = 0;
		if ( cAfter.getTimeInMillis() > cBefore.getTimeInMillis()) {
			int	fVal = cBefore.get( field);
			while ( cAfter.getTimeInMillis() >= cBefore.getTimeInMillis()) {
				count++;
				fVal = cBefore.get( field);
				cBefore.set( field, fVal + 1);
			}
			int result = count - 1;
			cBefore.set( field, fVal);
			return result;
		}
		return 0;
	}


	private static class DiaEvent implements Comparable<DiaEvent>{
		final long timestamp;
		final int amount;
		/** cumulatedAmount des Vorgängers + eigener Betrag */
		long	cumulatedAmount = -1;
		final DiaGenerator generator;
		final EventType type;

		public DiaEvent( long timestamp, int amount, EventType type, DiaGenerator generator) {
			super();
			this.timestamp = timestamp;
			this.amount = amount;
			this.generator = generator;
			this.type = type;
		}

		@Override
		public int compareTo( DiaEvent o) {
			if ( this == o) {
				return 0;
			}
			if ( timestamp != o.timestamp) {
				return Long.compare( timestamp, o.timestamp);
			}
			if ( this.generator instanceof Game) {
				return -1;
			}
			if ( o.generator instanceof Game) {
				return 1;
			}
			Mine	mThis = ( Mine) this.generator;
			Mine	mOther = ( Mine) o.generator;
			if ( mThis.timeCreated != mOther.timeCreated) {
				return Long.compare( mThis.timeCreated, mOther.timeCreated);
			}
			return 0;
		}

		@Override
		public String toString() {
			DiaGenerator	dg = generator;
			String	generator = null;
			if ( dg instanceof Game) {
				generator = "Spiel";
			} else {
				if ( dg instanceof Mine) {
					Mine m = ( Mine) dg;
					generator = "Mine " + dfYYYY_MM_DD.format( new Date ( m.timeCreated));
				}
			}
			return "" + new Date( timestamp)
					+ " " + amount
					+ " " + cumulatedAmount
					+ " " + generator;
		}
	}

	static private long getTotalAmount( List<DiaEvent> ownDiaEvents) {
		long	 sum = 0;
		for ( DiaEvent diaEvent : ownDiaEvents) {
			sum += diaEvent.amount;
		}
		return sum;
	}


	private interface DiaGenerator {
		List<DiaEvent> getEventsFromTo( Date start, Date end);
	}

	private static class Mine implements DiaGenerator {
		static final int BuildTime = 1000 * 60 * 10;
		static long ProdTimeMS = 1000 * ( 9 + 60*13);
		static int StartCapacity = 40000;
		static int Price = 20000;
		final long	timeCreated;
		final long timeToStorageMS;
		final long timeEmpty;
		private int index;

		public Mine( final long timeCreated, final long timeToStorageMS) {
			super();
			this.timeCreated = timeCreated;
			this.timeToStorageMS = timeToStorageMS;
			this.timeEmpty = timeCreated + StartCapacity * getTotalProdTime();
			System.out.println( "Mine from " + dfYYYY_MM_DD.format( new Date( timeCreated)) + " to " + dfYYYY_MM_DD.format( new Date( timeEmpty)));
		}

		/**
		 * ignoriert end, gibt immer alle restlichen aus
		 * @see DiaMine.DiaGenerator#getEventsFromTo(java.util.Date, java.util.Date)
		 */
		@Override
		public List<DiaEvent> getEventsFromTo( Date start, Date end) {
			List<DiaEvent> result = new ArrayList<DiaMine.DiaEvent>( StartCapacity);
			long	tsStart = start.getTime();
			if ( tsStart <= timeCreated - BuildTime) {
				result.add( new DiaEvent( timeCreated - BuildTime, -20000, EventType.Buy, this));
			}
			int	startAmount = getAmountAt( tsStart);
			long timeStamp = tsStart;
			result.add( new DiaEvent( timeStamp - 1, 0, EventType.Start, this));
			for	( int c = startAmount;  c > 0;  c--) {
				result.add( new DiaEvent( timeStamp, 1, EventType.Prod, this));
				timeStamp += ProdTimeMS + 2 * timeToStorageMS;
			}
			result.add( new DiaEvent( timeStamp + 1, 0, EventType.Finish, this));
			return result;
		}

		private int getAmountAt( long time) {
			long	timeSinceStart = time - timeCreated;
			return ( int) ( StartCapacity - ( timeSinceStart / getTotalProdTime()));
		}

		private long getTotalProdTime() {
			return ProdTimeMS + 2 * timeToStorageMS;
		}

		public static Mine WithAmountAndTransportS( int a, int tts, int jitterS) {
			long	timePerDia = ProdTimeMS + 2 * tts;
			long	diasProduced = StartCapacity - a;
			long	timeCreated = System.currentTimeMillis() - ( diasProduced * timePerDia) + jitterS * 1000;
			return new Mine( timeCreated, tts);
		}

		public void setIndex( int i) {
			index = i;
		}

		@Override
		public String toString() {
			return "Mine " + index + " " + dfYYYY_MM_DD.format( new Date ( timeCreated));
		}

	}

	private static class Game implements DiaGenerator {
		List<Mine> mines = new ArrayList<Mine>();
		TreeSet<DiaEvent> events = new TreeSet<DiaMine.DiaEvent>();

		long startTime;
		long endTime = System.currentTimeMillis() + 1000L * 86400L * 365 * 2;
		private long spentNow;

		public Game( int amount, Mine...m) {
			int totalGenerated = 0;
			startTime = Long.MAX_VALUE;
			for ( Mine mine : m) {
				mines.add( mine);
				mine.setIndex( mines.indexOf( mine));
				totalGenerated += ( Mine.StartCapacity - mine.getAmountAt( System.currentTimeMillis()));
				startTime = Math.min( startTime, mine.timeCreated);
			}
			// erster Dia aus Mine, hier beginnen wir die eigenen auch
			List<DiaEvent> ownDiaEvents = getEventsFromTo( new Date( startTime), new Date());
			long	ownDias = getTotalAmount( ownDiaEvents);
			spentNow = amount - ( totalGenerated + ownDias);
			System.out.println( "Game from " +  dfYYYY_MM_DD.format( new Date( startTime)) + " to " +  dfYYYY_MM_DD.format( new Date( endTime)) + ", " + events.size() + " events");
		}


		@Override
		public List<DiaEvent> getEventsFromTo( Date start, Date end) {
			long	startTime = start.getTime();
			long	weekMS = 1000 * 60 * 60 * 24 * 7;
			List<DiaEvent> result = new ArrayList<DiaMine.DiaEvent>();
			for ( long t = startTime;  t < end.getTime();  t += weekMS) {
				result.add( new DiaEvent( t, 100, EventType.Weekly, this));
			}
			return result;
		}

		public void simulate() {
			initSimulation();
			Date	currentTime = new Date();
			while ( currentTime.getTime() < endTime) {
				// gucke, ab wann das Geld für neue Mine reicht und eine Mine reinkann
				DiaEvent currentEvent = getEventAtOrAfter( currentTime.getTime());
				DiaEvent	nextPurchase = getNextPurchase( currentEvent);
				events.add( nextPurchase);
				addMine( nextPurchase);
				recumulateAt( nextPurchase);
				currentTime.setTime( nextPurchase.timestamp + 1000);
				System.out.println( "added Mine at: " +  dfYYYY_MM_DD.format( new Date( nextPurchase.timestamp)) + ", " + events.size() + " events, balance " + nextPurchase.cumulatedAmount);
				System.out.println();
			}
			System.out.println( "at " + new Date( endTime) + ", " + events.size() + " events");
			try {
				write( "events.csv");
			} catch ( FileNotFoundException e) {
				e.printStackTrace();
			}
		}


		private void write( String filename) throws FileNotFoundException {
			PrintStream outStr = new PrintStream( filename);
			outStr.println( "Zeit\tTyp\tMenge\tSumme\tQuelle");
			DiaEvent[]	ev = events.toArray( new DiaEvent[0]);
			for ( int i = 0; i < ev.length; i++) {
				DiaEvent diaEvent = ev[ i];
				int	h = Math.max( i-1,  0);
				int	j = Math.min( i+1,  ev.length-1);
				if ( diaEvent.type == EventType.Prod && ev[h].type == EventType.Prod && ev[j].type == EventType.Prod) {
					continue;
				}
				DiaGenerator	dg = diaEvent.generator;
				String	generator = null;
				if ( dg == this) {
					generator = "Spiel";
				} else {
					if ( dg instanceof Mine) {
						Mine m = ( Mine) dg;
						generator = m.toString();
					}
				}
				outStr.println( ""
						+ dfYYYY_MM_DD_HH_MM_SS.format( new Date( diaEvent.timestamp))
						+ "\t" + diaEvent.type
						+ "\t" + diaEvent.amount
						+ "\t" + diaEvent.cumulatedAmount
						+ "\t" + generator
						);
			}
			outStr.close();
		}


		private void addMine( DiaEvent nextPurchase) {
			Mine	mine = new Mine( nextPurchase.timestamp + Mine.BuildTime, 52);
			mines.add( mine);
			mine.setIndex( mines.indexOf( mine));
			events.addAll( mine.getEventsFromTo( new Date( mine.timeCreated + mine.getTotalProdTime()), null));
		}


		private void recumulateAt( DiaEvent nextPurchase) {
			DiaEvent before = events.lower( nextPurchase);
			nextPurchase.cumulatedAmount = before.cumulatedAmount + nextPurchase.amount;
			NavigableSet<DiaEvent> after = events.tailSet( nextPurchase, false);
			cumulate( after, nextPurchase.cumulatedAmount);
		}


		private DiaEvent getNextPurchase( DiaEvent currentEvent) {
			NavigableSet<DiaEvent> after = events.tailSet( currentEvent, false);
			Date waitingSince = null;
			for ( DiaEvent diaEvent : after) {
				if ( diaEvent.cumulatedAmount > Mine.Price) {
					if ( getNumMines( diaEvent.timestamp) < 10) {
						if ( waitingSince != null) {
							final long waited = diaEvent.timestamp - waitingSince.getTime();
							final String waitedS = getOffsetAsString( diaEvent.timestamp, waitingSince.getTime());
							System.out.println( "waited for Mine " + waitedS + " (" + ( waited / 86400000) + " T)");
						}
						return new DiaEvent( diaEvent.timestamp + 1000, - Mine.Price, EventType.Buy, this);
					}
					if ( waitingSince == null) {
						waitingSince = new Date( diaEvent.timestamp);
					}
				}
			}
			return null;
		}


		private int getNumMines( long timestamp) {
			int	count = 0;
			for ( Mine mine : mines) {
				if ( ( mine.timeCreated - Mine.BuildTime) < timestamp && mine.timeEmpty >= timestamp) {
					count++;
				}
			}
			return count;
		}


		private DiaEvent getEventAtOrAfter( long ts) {
			for ( DiaEvent diaEvent : events) {
				if ( diaEvent.timestamp >= ts) {
					return diaEvent;
				}
			}
			return null;
		}


		private void initSimulation() {
			// nimm alle Events, die wir schon kennen
			List<DiaEvent> ownDiaEvents = getEventsFromTo( new Date( startTime), new Date( endTime));
			events.addAll( ownDiaEvents);
			for ( Mine mine : mines) {
				events.addAll( mine.getEventsFromTo( new Date( mine.timeCreated + mine.getTotalProdTime()), null));
			}
			cumulate( events, spentNow);
		}


		private void cumulate(SortedSet<DiaEvent> eventsSorted, long startBalance) {
			long	balance = startBalance;
			for ( DiaEvent diaEvent : eventsSorted) {
				balance += diaEvent.amount;
				diaEvent.cumulatedAmount = balance;
			}
		}

	}
	PriorityQueue<DiaEvent> events = new PriorityQueue<DiaEvent>();

	public static void main( String[] args) {
		int	currentAmount = getCurrentAmount( args);
		Mine[]	mines = getCurrentMines( args);
//		// die Minen, jetzt
//		Mine m0 = Mine.WithAmountAndTransportS( 790, 54, 10);
//		Mine m1 = Mine.WithAmountAndTransportS( 19798, 58, 20);
//		Mine m2 = Mine.WithAmountAndTransportS( 29137, 52, 30);
//		Mine m3 = Mine.WithAmountAndTransportS( 35665, 56, 40);
		System.out.println( "Current Amount: " + currentAmount);
		Game	g = new Game( currentAmount, mines);
		g.simulate();
	}

	private static Mine[] getCurrentMines( String[] args) {
		String	argName = "-m";
		List<Mine> mines = new ArrayList<DiaMine.Mine>();
		for ( String arg : args) {
			if ( arg.startsWith( argName) && arg.length() > argName.length()) {
				String	mineS = arg.substring( 2);
				String[]	info = mineS.split( ":");
				int a = Integer.valueOf( info[ 0]);
				int tts = Integer.valueOf( info[ 1]);
				int jitterS = mines.size() * 10;
				Mine mine = Mine.WithAmountAndTransportS( a, tts, jitterS);
				mines.add( mine);
			}
		}
		System.out.println( "Mines: " + mines);
		return mines.toArray( new Mine[0]);
	}

	private static int getCurrentAmount( String[] args) {
		String	argName = "-a";
		for ( String arg : args) {
			if ( arg.startsWith( argName) && arg.length() > argName.length()) {
				String	ams = arg.substring( 2);
				int	amount = Integer.valueOf( ams);
				return amount;
			}
		}
		return 0;
	}
}
