// This file is part of OpenTSDB.
// Copyright (C) 2014  The OpenTSDB Authors.
//
// This program is free software: you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by
// the Free Software Foundation, either version 2.1 of the License, or (at your
// option) any later version.  This program is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
// of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
// General Public License for more details.  You should have received a copy
// of the GNU Lesser General Public License along with this program.  If not,
// see <http://www.gnu.org/licenses/>.
package net.opentsdb.query.processor.downsample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Period;
import java.time.ZoneId;
import java.util.Iterator;

import net.opentsdb.data.BaseTimeSeriesId;
import net.opentsdb.data.MillisecondTimeStamp;
import net.opentsdb.data.MockTimeSeries;
import net.opentsdb.data.TimeSeries;
import net.opentsdb.data.TimeSeriesValue;
import net.opentsdb.data.TimeStamp;
import net.opentsdb.data.TimeStamp.RelationalOperator;
import net.opentsdb.data.types.numeric.MutableNumericType;
import net.opentsdb.data.types.numeric.NumericMillisecondShard;
import net.opentsdb.data.types.numeric.NumericType;
import net.opentsdb.query.QueryContext;
import net.opentsdb.query.QueryNode;
import net.opentsdb.query.QueryPipelineContext;
import net.opentsdb.query.TimeSeriesQuery;
import net.opentsdb.query.QueryFillPolicy.FillWithRealPolicy;
import net.opentsdb.query.interpolation.types.numeric.NumericInterpolatorConfig;
import net.opentsdb.query.interpolation.types.numeric.NumericInterpolatorFactory;
import net.opentsdb.query.interpolation.types.numeric.ScalarNumericInterpolatorConfig;
import net.opentsdb.query.pojo.FillPolicy;
import net.opentsdb.query.pojo.Metric;
import net.opentsdb.query.pojo.Timespan;

import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class TestDownsampler {

  private TimeSeries source;
  private TimeSeriesQuery query;
  private DownsampleConfig config;
  private QueryNode node;
  private QueryContext query_context;
  private QueryPipelineContext pipeline_context;
  
  private static final long BASE_TIME = 1356998400000L;
  //30 minute offset
  final static ZoneId AF = ZoneId.of("Asia/Kabul");
  // 12h offset w/o DST
  final static ZoneId TV = ZoneId.of("Pacific/Funafuti");
  // 12h offset w DST
  final static ZoneId FJ = ZoneId.of("Pacific/Fiji");
  // Tue, 15 Dec 2015 04:02:25.123 UTC
  final static long DST_TS = 1450137600000L;
  
  @Before
  public void before() {
    
  }
  
  @Test
  public void ctor() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
      new MillisecondTimeStamp(BASE_TIME), 
      new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 2000000, 50);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 10000000))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    assertTrue(it.hasNext());
    
    when(node.config()).thenReturn(null);
    try {
      new DownsampleNumericIterator(node, source);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
    
    try {
      new DownsampleNumericIterator(null, source);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
    
    try {
      new DownsampleNumericIterator(node, null);
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) { }
  }
  
  @Test
  public void downsample1000seconds() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 2000000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3600000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3605000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 7200000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 9200000, 50);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 10000000))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357000400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357001400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356999400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357000400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357001400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357003400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357004400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357006400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357008400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertFalse(it.hasNext());
  }

  @Test
  public void downsample10Seconds() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 0, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 1, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 2, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 3, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 4, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 5, 32);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 6, 64);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 7, 128);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 8, 256);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 9, 512);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 10, 1024);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 5000L * 10))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    final DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
        
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998420000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(192, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998450000L, v.timestamp().msEpoch());
    assertEquals(1024, v.value().longValue());
    
    assertFalse(it.hasNext());
  }

  @Test
  public void downsample15Seconds() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32); // falls outside of end interval
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 55000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("15s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    final DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(1, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998415000L, v.timestamp().msEpoch());
    assertEquals(6, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(8, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998445000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleDoubles() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1.5);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2.75);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4.0);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8.25);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16.33);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32.6); // falls outside of end interval
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 55000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("15s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    final DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(1.5, v.value().doubleValue(), 0.001);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998415000L, v.timestamp().msEpoch());
    assertEquals(6.75, v.value().doubleValue(), 0.001);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(8.25, v.value().doubleValue(), 0.001);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998445000L, v.timestamp().msEpoch());
    assertEquals(48.93, v.value().doubleValue(), 0.001);
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleLoneDouble() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8.75);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32); // falls outside of end interval
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 55000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("15s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    final DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(1, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998415000L, v.timestamp().msEpoch());
    assertEquals(6, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(8.75, v.value().doubleValue(), 0.001);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998445000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleLongAndDoubleAgged() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4.5);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32); // falls outside of end interval
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 55000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("15s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    final DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(1, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998415000L, v.timestamp().msEpoch());
    assertEquals(6.5, v.value().doubleValue(), 0.001);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(8, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998445000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleDoubleAndLongAgged() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2.5);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32); // falls outside of end interval
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 55000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("15s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    final DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(1, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998415000L, v.timestamp().msEpoch());
    assertEquals(6.5, v.value().doubleValue(), 0.001);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(8, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998445000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsample10SecondsFilterOnQuery() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
      new MillisecondTimeStamp(BASE_TIME), 
      new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 0, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 1, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 2, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 3, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 4, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 5, 32);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 6, 64);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 7, 128);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 8, 256);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 9, 512);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 10, 1024);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(1356998410000L))
            .setEnd(Long.toString(1356998440000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);

    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998420000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(192, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998420000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(192, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsample10SecondsFilterOnQueryLate() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
      new MillisecondTimeStamp(BASE_TIME), 
      new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 0, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 1, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 2, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 3, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 4, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 5, 32);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 6, 64);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 7, 128);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 8, 256);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 9, 512);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 10, 1024);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(1356998380000L))
            .setEnd(Long.toString(1356998420000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);

    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998420000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998380000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998390000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998420000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsample10SecondsFilterOnQueryEarly() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
      new MillisecondTimeStamp(BASE_TIME), 
      new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 0, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 1, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 2, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 3, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 4, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 5, 32);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 6, 64);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 7, 128);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 8, 256);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 9, 512);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 10, 1024);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(1356998440000L))
            .setEnd(Long.toString(1356998460000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);

    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998450000L, v.timestamp().msEpoch());
    assertEquals(1024, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998450000L, v.timestamp().msEpoch());
    assertEquals(1024, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998460000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsample10SecondsFilterOnQueryOutOfRangeLate() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
      new MillisecondTimeStamp(BASE_TIME), 
      new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 0, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 1, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 2, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 3, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 4, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 5, 32);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 6, 64);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 7, 128);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 8, 256);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 9, 512);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 10, 1024);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME - 30000L))
            .setEnd(Long.toString(BASE_TIME - 10000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsample10SecondsFilterOnQueryOutOfRangeEarly() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
      new MillisecondTimeStamp(BASE_TIME), 
      new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 0, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 1, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 2, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 3, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 4, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 5, 32);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 6, 64);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 7, 128);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 8, 256);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 9, 512);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L * 10, 1024);
    Iterator<TimeSeriesValue<?>> i = source.iterator(NumericType.TYPE).get();
    while (i.hasNext()) {
      TimeSeriesValue<NumericType> v=  (TimeSeriesValue<NumericType>) i.next();
      System.out.println(v.timestamp() + " " + v.value().longValue());
    }
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(1356998460000L))
            .setEnd(Long.toString(1356998480000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleAll() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 55000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("0all")
        .setQuery(query)
        .setRunAll(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(63, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("0all")
        .setQuery(query)
        .setFill(true)
        .setRunAll(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(63, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleAllFilterOnQuery() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME + 15000L))
            .setEnd(Long.toString(BASE_TIME + 45000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("0all")
        .setQuery(query)
        .setRunAll(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998415000L, v.timestamp().msEpoch());
    assertEquals(30, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("0all")
        .setQuery(query)
        .setFill(true)
        .setRunAll(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998415000L, v.timestamp().msEpoch());
    assertEquals(30, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleAllFilterOnQueryOutOfRangeEarly() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME + 65000L))
            .setEnd(Long.toString(BASE_TIME + 75000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("0all")
        .setQuery(query)
        .setRunAll(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("0all")
        .setQuery(query)
        .setFill(true)
        .setRunAll(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleAllFilterOnQueryOutOfRangeLate() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME - 15000L))
            .setEnd(Long.toString(BASE_TIME - 5000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("0all")
        .setQuery(query)
        .setRunAll(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("0all")
        .setQuery(query)
        .setFill(true)
        .setRunAll(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleCalendar() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME + 5000L, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 15000L, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 25000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 35000L, 8);
    ((NumericMillisecondShard) source).add(BASE_TIME + 45000L, 16);
    ((NumericMillisecondShard) source).add(BASE_TIME + 55000L, 32);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart("2012/12/31-07:00:00")
            .setEnd("2013/01/01-07:00:00")
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setQuery(query)
        .setTimeZone(ZoneId.of("America/Denver"))
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    final DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356937200000L, v.timestamp().msEpoch());
    assertEquals(63, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleCalendarHour() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 1800000, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3599000L, 3);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3600000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 5400000L, 5);
    ((NumericMillisecondShard) source).add(BASE_TIME + 7199000L, 6);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 8000000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1h")
        .setQuery(query)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(6, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002000000L, v.timestamp().msEpoch());
    assertEquals(15, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1h")
        .setQuery(query)
        .setFill(true)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(6, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002000000L, v.timestamp().msEpoch());
    assertEquals(15, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005600000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertFalse(it.hasNext());
    
    // 12 hour offset
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1h")
        .setQuery(query)
        .setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(6, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002000000L, v.timestamp().msEpoch());
    assertEquals(15, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // 30 minute offset with a different timezone
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1h")
        .setQuery(query)
        .setTimeZone(AF)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    // filters out the first value.
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357000200000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357003800000L, v.timestamp().msEpoch());
    assertEquals(11, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // multi-hour downsample
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME - (3600000L * 4)))
            .setEnd(Long.toString(BASE_TIME + (3600000L * 4)))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("4h")
        .setQuery(query)
        .setTimeZone(AF)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356996600000L, v.timestamp().msEpoch());
    assertEquals(21, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleCalendarDay() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(1357257600000L));
    ((NumericMillisecondShard) source).add(BASE_TIME, 1);
    ((NumericMillisecondShard) source).add(BASE_TIME + 86399000, 2);
    ((NumericMillisecondShard) source).add(BASE_TIME + 126001000L, 3);
    ((NumericMillisecondShard) source).add(BASE_TIME + 172799000L, 4);
    ((NumericMillisecondShard) source).add(BASE_TIME + 172800000L, 5);
    ((NumericMillisecondShard) source).add(BASE_TIME + 242999000L, 6);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 259200000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setQuery(query)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357084800000L, v.timestamp().msEpoch());
    assertEquals(7, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357171200000L, v.timestamp().msEpoch());
    assertEquals(11, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setQuery(query)
        .setFill(true)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357084800000L, v.timestamp().msEpoch());
    assertEquals(7, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357171200000L, v.timestamp().msEpoch());
    assertEquals(11, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357257600000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertFalse(it.hasNext());
    
    // 12 hour offset from UTC
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setQuery(query)
        .setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);

    // first point skipped due to query time filter
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357041600000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357128000000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357214400000L, v.timestamp().msEpoch());
    assertEquals(6, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // 11 hour offset from UTC
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setQuery(query)
        .setTimeZone(FJ)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    // first point skipped due to query time filter
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357038000000L, v.timestamp().msEpoch());
    assertEquals(2, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357124400000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357210800000L, v.timestamp().msEpoch());
    assertEquals(6, v.value().longValue());
    
    // last is out of bounds
    assertFalse(it.hasNext());
    
    // 30m offset
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setQuery(query)
        .setTimeZone(AF)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);

    // first point skipped due to query time filter
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357068600000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357155000000L, v.timestamp().msEpoch());
    assertEquals(15, v.value().longValue());
    
    assertFalse(it.hasNext());

    // multiple days
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(1356982200000L))
            .setEnd(Long.toString(1357257600000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("3d")
        .setQuery(query)
        .setTimeZone(AF)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356982200000L, v.timestamp().msEpoch());
    assertEquals(21, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleCalendarWeek() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(DST_TS), 
        new MillisecondTimeStamp(1452384000000L));
    ((NumericMillisecondShard) source).add(DST_TS, 1); // a Tuesday in UTC land
    ((NumericMillisecondShard) source).add(DST_TS + (86400000L * 7), 2);
    ((NumericMillisecondShard) source).add(1451129400000L, 3); // falls to the next in FJ
    ((NumericMillisecondShard) source).add(DST_TS + (86400000L * 21), 4);
    ((NumericMillisecondShard) source).add(1452367799000L, 5); // falls within 30m offset
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart("2015/12/13-00:00:00")
            .setEnd("2016/01/10-00:00:00")
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1w")
        .setQuery(query)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1449964800000L, v.timestamp().msEpoch());
    assertEquals(1, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1450569600000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
        
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451779200000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    assertFalse(it.hasNext());

    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1w")
        .setQuery(query)
        .setFill(true)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1449964800000L, v.timestamp().msEpoch());
    assertEquals(1, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1450569600000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451174400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451779200000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1452384000000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertFalse(it.hasNext());
    
    // 12 hour offset from UTC
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1w")
        .setQuery(query)
        .setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    // first filtered by query times
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1450526400000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451736000000L, v.timestamp().msEpoch());
    assertEquals(4, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1452340800000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // 11 hour offset from UTC
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1w")
        .setQuery(query)
        .setTimeZone(FJ)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    // first filtered by query times
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1450522800000L, v.timestamp().msEpoch());
    assertEquals(2, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451127600000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451732400000L, v.timestamp().msEpoch());
    assertEquals(4, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1452337200000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // 30m offset
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1w")
        .setQuery(query)
        .setTimeZone(AF)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    // first filtered by query times
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1450553400000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451763000000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    assertFalse(it.hasNext());

    // multiple weeks
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart("2015/12/05-00:00:00")
            .setEnd("2016/01/10-00:00:00")
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("2w")
        .setQuery(query)
        .setTimeZone(AF)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1449343800000L, v.timestamp().msEpoch());
    assertEquals(1, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1450553400000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451763000000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleCalendarMonth() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(1448928000000L), 
        new MillisecondTimeStamp(1483297200000L));
    ((NumericMillisecondShard) source).add(1448928000000L, 1); // Dec 1st
    ((NumericMillisecondShard) source).add(1451559600000L, 2); // falls to the next in FJ
    ((NumericMillisecondShard) source).add(1451606400000L, 3); // Jan 1st
    ((NumericMillisecondShard) source).add(1454284800000L, 4); // Feb 1st
    ((NumericMillisecondShard) source).add(1456704000000L, 5); // Feb 29th (leap year)
    ((NumericMillisecondShard) source).add(1483297200000L, 6); // falls within 30m offset AF
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart("2015/12/01-00:00:00")
            .setEnd("2016/02/29-19:00:00")
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1n")
        .setQuery(query)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1448928000000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451606400000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1454284800000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // 12h offset    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1n")
        .setQuery(query)
        .setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451563200000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1454241600000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    // last is out of bounds
    assertFalse(it.hasNext());
    
    // 11h offset
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1n")
        .setQuery(query)
        .setTimeZone(FJ)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451559600000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1454241600000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());

    // last is out of bounds
    assertFalse(it.hasNext());
    
    // 30m offset
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1n")
        .setQuery(query)
        .setTimeZone(AF)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    // first bits cutoff due to filter
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451590200000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1454268600000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    // last is out of bounds
    assertFalse(it.hasNext());
    
    // multiple months
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart("2015/12/01-00:00:00")
            .setEnd("2016/04/29-19:00:00")
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("3n")
        .setQuery(query)
        .setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    // some filtered out on query time.
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451563200000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleCalendarYears() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(1356998400000L), 
        new MillisecondTimeStamp(1514833200000L));
    ((NumericMillisecondShard) source).add(1356998400000L, 1); // Jan 1st 2013
    ((NumericMillisecondShard) source).add(1388534400000L, 2); // Jan 1st 2014
    ((NumericMillisecondShard) source).add(1420054260000L, 3); // Dec 31st 2014 at 19:31 so it falls right in AF
    ((NumericMillisecondShard) source).add(1451606400000L, 4); // Jan 1st 2016
    ((NumericMillisecondShard) source).add(1483228800000L, 5); // Jan 1st 2017
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart("2013/01/01-00:00:00")
            .setEnd("2017/01/01-00:00:00")
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1y")
        .setQuery(query)
        //.setTimeZone(AF)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(1, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1388534400000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451606400000L, v.timestamp().msEpoch());
    assertEquals(4, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1483228800000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // 12h offset    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1y")
        .setQuery(query)
        .setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    // first filtered out
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1388491200000L, v.timestamp().msEpoch());
    assertEquals(2, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1420027200000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451563200000L, v.timestamp().msEpoch());
    assertEquals(4, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1483185600000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // 11h offset
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1y")
        .setQuery(query)
        .setTimeZone(FJ)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1388487600000L, v.timestamp().msEpoch());
    assertEquals(2, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1420023600000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451559600000L, v.timestamp().msEpoch());
    assertEquals(4, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1483182000000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // 30m offset
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1y")
        .setQuery(query)
        .setTimeZone(AF)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);

    // first bits cutoff due to filter
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1388518200000L, v.timestamp().msEpoch());
    assertEquals(2, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1420054200000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1451590200000L, v.timestamp().msEpoch());
    assertEquals(4, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1483212600000L, v.timestamp().msEpoch());
    assertEquals(5, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // multiple years
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart("2013/01/01-00:00:00")
            .setEnd("2018/01/01-00:00:00")
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("3y")
        .setQuery(query)
        .setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    // some filtered out on query time.
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1420070400000L, v.timestamp().msEpoch());
    assertEquals(9, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsamplerNoData() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(1448928000000L), 
        new MillisecondTimeStamp(1456772400000L));
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart("2012/12/31-07:00:00")
            .setEnd("2013/01/01-07:00:00")
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setQuery(query)
        .setTimeZone(ZoneId.of("America/Denver"))
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setQuery(query)
        .setTimeZone(ZoneId.of("America/Denver"))
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampler1monthAlt() {
    /*
    1380600000 -> 2013-10-01T04:00:00Z
    1383278400 -> 2013-11-01T04:00:00Z
    1385874000 -> 2013-12-01T05:00:00Z
    1388552400 -> 2014-01-01T05:00:00Z
    1391230800 -> 2014-02-01T05:00:00Z
    1393650000 -> 2014-03-01T05:00:00Z
    1396324800 -> 2014-04-01T04:00:00Z
    1398916800 -> 2014-05-01T04:00:00Z
    1401595200 -> 2014-06-01T04:00:00Z
    1404187200 -> 2014-07-01T04:00:00Z
    1406865600 -> 2014-08-01T04:00:00Z
    1409544000 -> 2014-09-01T04:00:00Z
    */
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(1380585600000L), 
        new MillisecondTimeStamp(1409544000000L));
    ((NumericMillisecondShard) source).add(1380600000000L, 1);
    ((NumericMillisecondShard) source).add(1383278400000L, 1);
    ((NumericMillisecondShard) source).add(1385874000000L, 1);
    ((NumericMillisecondShard) source).add(1388552400000L, 1);
    ((NumericMillisecondShard) source).add(1391230800000L, 1);
    ((NumericMillisecondShard) source).add(1393650000000L, 1);
    ((NumericMillisecondShard) source).add(1396324800000L, 1);
    ((NumericMillisecondShard) source).add(1398916800000L, 1);
    ((NumericMillisecondShard) source).add(1401595200000L, 1);
    ((NumericMillisecondShard) source).add(1404187200000L, 1);
    ((NumericMillisecondShard) source).add(1406865600000L, 1);
    ((NumericMillisecondShard) source).add(1409544000000L, 1);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(1380585600000L))
            .setEnd(Long.toString(1409544000000L))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setFill(true)
        .setQuery(query)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    TimeStamp daily = new MillisecondTimeStamp(1380585600000L);
    TimeStamp monthly = new MillisecondTimeStamp(1380585600000L);
    
    int iterations = 0;
    while (it.hasNext()) {
      TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
      assertTrue(daily.compare(RelationalOperator.EQ, v.timestamp()));
      if (monthly.compare(RelationalOperator.EQ, v.timestamp())) {
        assertEquals(1, v.value().longValue());
        monthly.add(Period.ofMonths(1));
      } else {
        assertNull(v.value());
      }
      daily.add(Period.ofDays(1));
      iterations++;
    }
    assertEquals(336, iterations); // last value is skipped as it's out of bounds.
    
    // no fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("1d")
        .setQuery(query)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    daily = new MillisecondTimeStamp(1380585600000L);
    monthly = new MillisecondTimeStamp(1380585600000L);
    
    iterations = 0;
    while (it.hasNext()) {
      TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
      assertTrue(monthly.compare(RelationalOperator.EQ, v.timestamp()));
      assertEquals(1, v.value().longValue());
      monthly.add(Period.ofMonths(1));
      iterations++;
    }
    assertEquals(12, iterations); // last value is skipped as it's out of bounds.
  }
  
  @Test
  public void downsamplerSkipPartialInterval() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 9200000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 2000000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3600000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3605000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 7200000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 9200000, 50);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME + 3800000L))
            .setEnd(Long.toString(BASE_TIME + 10000000L))
            .setAggregator("avg"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setFill(true)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    // seek timestamp was BASE_TIME + 3800000L or 1,357,002,200,000 ms.
    // The interval that has the timestamp began at 1,357,002,000,000 ms. It
    // had two data points but was abandoned because the requested timestamp
    // was not aligned. The next two intervals at 1,357,003,000,000 and
    // at 1,357,004,000,000 did not have data points. The first interval that
    // had a data point began at 1,357,002,005,000 ms or BASE_TIME + 6600000L.
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357003400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357004400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357006400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357008400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertFalse(it.hasNext());
    
    // no fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        //.setFill(true)
        //.setTimeZone(TV)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
    
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleNullAtStart() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new MockTimeSeries(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build());
    MutableNumericType nully = new MutableNumericType();
    nully.resetNull(new MillisecondTimeStamp(BASE_TIME + 5000L * 0));
    ((MockTimeSeries) source).addValue(nully);
    //((MockTimeSeries) source).addValue(new MutableNumericType(
    //    new MillisecondTimeStamp(BASE_TIME + 5000L * 0), 1));
    nully = new MutableNumericType();
    nully.resetNull(new MillisecondTimeStamp(BASE_TIME + 5000L * 2));
    ((MockTimeSeries) source).addValue(nully);
    //((MockTimeSeries) source).addValue(new MutableNumericType(
    //    new MillisecondTimeStamp(BASE_TIME + 5000L * 1), 2));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 2), 4));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 3), 8));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 4), 16));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 5), 32));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 6), 64));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 7), 128));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 8), 256));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 9), 512));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 10), 1024));
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 5000L * 10))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998420000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(192, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998450000L, v.timestamp().msEpoch());
    assertEquals(1024, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
        
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998420000L, v.timestamp().msEpoch());
    assertEquals(48, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(192, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998450000L, v.timestamp().msEpoch());
    assertEquals(1024, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleNullInMiddleOnBoundary() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new MockTimeSeries(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build());
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 0), 1));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 1), 2));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 2), 4));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 3), 8));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 4), 16));
    MutableNumericType nully = new MutableNumericType();
    nully.resetNull(new MillisecondTimeStamp(BASE_TIME + 5000L * 5));
    ((MockTimeSeries) source).addValue(nully);
    //((MockTimeSeries) source).addValue(new MutableNumericType(
    //    new MillisecondTimeStamp(BASE_TIME + 5000L * 5), 32));
    nully = new MutableNumericType();
    nully.resetNull(new MillisecondTimeStamp(BASE_TIME + 5000L * 6));
    ((MockTimeSeries) source).addValue(nully);
    //((MockTimeSeries) source).addValue(new MutableNumericType(
    //    new MillisecondTimeStamp(BASE_TIME + 5000L * 6), 64));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 7), 128));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 8), 256));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 9), 512));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 10), 1024));
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 5000L * 10))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    final DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
        
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998420000L, v.timestamp().msEpoch());
    assertEquals(16, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(128, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998450000L, v.timestamp().msEpoch());
    assertEquals(1024, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleNullInMiddleInBoundary() {
    // behaves the same with the difference that the old version would return the
    // first value at BASE_TIME but now we skip it.
    source = new MockTimeSeries(BaseTimeSeriesId.newBuilder()
        .setMetric("a")
        .build());
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 0), 1));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 1), 2));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 2), 4));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 3), 8));
    MutableNumericType nully = new MutableNumericType();
    nully.resetNull(new MillisecondTimeStamp(BASE_TIME + 5000L * 4));
    ((MockTimeSeries) source).addValue(nully);
    nully = new MutableNumericType();
    nully.resetNull(new MillisecondTimeStamp(BASE_TIME + 5000L * 6));
    ((MockTimeSeries) source).addValue(nully);
    //((MockTimeSeries) source).addValue(new MutableNumericType(
    //    new MillisecondTimeStamp(BASE_TIME + 5000L * 4), 16));
    //((MockTimeSeries) source).addValue(new MutableNumericType(
    //    new MillisecondTimeStamp(BASE_TIME + 5000L * 5), 32));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 6), 64));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 7), 128));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 8), 256));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 9), 512));
    ((MockTimeSeries) source).addValue(new MutableNumericType(
        new MillisecondTimeStamp(BASE_TIME + 5000L * 10), 1024));
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 5000L * 10))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();
    
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
        
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(192, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998450000L, v.timestamp().msEpoch());
    assertEquals(1024, v.value().longValue());
    
    assertFalse(it.hasNext());
    
    // fill
    config = DownsampleConfig.newBuilder()
        .setAggregator("sum")
        .setId("foo")
        .setInterval("10s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    it = new DownsampleNumericIterator(node, source);
        
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(3, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998410000L, v.timestamp().msEpoch());
    assertEquals(12, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998420000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998430000L, v.timestamp().msEpoch());
    assertEquals(192, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998440000L, v.timestamp().msEpoch());
    assertEquals(768, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998450000L, v.timestamp().msEpoch());
    assertEquals(1024, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleFillNaNs() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 2000000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3600000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3605000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 7200000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 9200000, 50);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 10000000))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();

    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NOT_A_NUMBER)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356999400000L, v.timestamp().msEpoch());
    assertTrue(Double.isNaN(v.value().doubleValue()));
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357000400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357001400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002400000L, v.timestamp().msEpoch());
    assertTrue(Double.isNaN(v.value().doubleValue()));
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357003400000L, v.timestamp().msEpoch());
    assertTrue(Double.isNaN(v.value().doubleValue()));
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357004400000L, v.timestamp().msEpoch());
    assertTrue(Double.isNaN(v.value().doubleValue()));
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357006400000L, v.timestamp().msEpoch());
    assertTrue(Double.isNaN(v.value().doubleValue()));
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357008400000L, v.timestamp().msEpoch());
    assertTrue(Double.isNaN(v.value().doubleValue()));
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleFillNulls() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 2000000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3600000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3605000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 7200000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 9200000, 50);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 10000000))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();

    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NULL)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356999400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357000400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357001400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357003400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357004400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357006400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357008400000L, v.timestamp().msEpoch());
    assertNull(v.value());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleFillZeros() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 2000000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3600000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3605000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 7200000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 9200000, 50);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 10000000))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();

    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.ZERO)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356999400000L, v.timestamp().msEpoch());
    assertEquals(0, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357000400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357001400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002400000L, v.timestamp().msEpoch());
    assertEquals(0, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357003400000L, v.timestamp().msEpoch());
    assertEquals(0, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357004400000L, v.timestamp().msEpoch());
    assertEquals(0, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357006400000L, v.timestamp().msEpoch());
    assertEquals(0, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357008400000L, v.timestamp().msEpoch());
    assertEquals(0, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleFillScalar() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 2000000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3600000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3605000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 7200000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 9200000, 50);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 10000000))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();

    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(ScalarNumericInterpolatorConfig.newBuilder()
            .setValue(42)
            .setFillPolicy(FillPolicy.SCALAR)
            .setRealFillPolicy(FillWithRealPolicy.NONE)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356999400000L, v.timestamp().msEpoch());
    assertEquals(42, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357000400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357001400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002400000L, v.timestamp().msEpoch());
    assertEquals(42, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357003400000L, v.timestamp().msEpoch());
    assertEquals(42, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357004400000L, v.timestamp().msEpoch());
    assertEquals(42, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357006400000L, v.timestamp().msEpoch());
    assertEquals(42, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357008400000L, v.timestamp().msEpoch());
    assertEquals(42, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleFillPreferNext() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 2000000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3600000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3605000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 7200000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 9200000, 50);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 10000000))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();

    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.PREFER_NEXT)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356999400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357000400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357001400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357003400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357004400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357006400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357008400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  @Test
  public void downsampleFillPreferPrevious() {
    source = new NumericMillisecondShard(BaseTimeSeriesId.newBuilder()
          .setMetric("a")
          .build(), 
        new MillisecondTimeStamp(BASE_TIME), 
        new MillisecondTimeStamp(BASE_TIME + 10000000));
    ((NumericMillisecondShard) source).add(BASE_TIME, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 2000000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3600000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 3605000, 50);
    ((NumericMillisecondShard) source).add(BASE_TIME + 7200000, 40);
    ((NumericMillisecondShard) source).add(BASE_TIME + 9200000, 50);
    
    query = net.opentsdb.query.pojo.TimeSeriesQuery.newBuilder()
        .setTime(Timespan.newBuilder()
            .setStart(Long.toString(BASE_TIME))
            .setEnd(Long.toString(BASE_TIME + 10000000))
            .setAggregator("sum"))
        .addMetric(Metric.newBuilder()
            .setId("m1")
            .setMetric("a"))
        .build();

    config = DownsampleConfig.newBuilder()
        .setAggregator("avg")
        .setId("foo")
        .setInterval("1000s")
        .setQuery(query)
        .setFill(true)
        .setQueryIteratorInterpolatorFactory(new NumericInterpolatorFactory.Default())
        .setQueryIteratorInterpolatorConfig(NumericInterpolatorConfig.newBuilder()
            .setFillPolicy(FillPolicy.NONE)
            .setRealFillPolicy(FillWithRealPolicy.PREFER_PREVIOUS)
            .build())
        .build();
    
    setupMock();
    DownsampleNumericIterator it = new DownsampleNumericIterator(node, source);
    
    assertTrue(it.hasNext());
    TimeSeriesValue<NumericType> v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356998400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1356999400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357000400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357001400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357002400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357003400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357004400000L, v.timestamp().msEpoch());
    assertEquals(45, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357005400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357006400000L, v.timestamp().msEpoch());
    assertEquals(40, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357007400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertTrue(it.hasNext());
    v = (TimeSeriesValue<NumericType>) it.next();
    assertEquals(1357008400000L, v.timestamp().msEpoch());
    assertEquals(50, v.value().longValue());
    
    assertFalse(it.hasNext());
  }
  
  private void setupMock() {
    node = mock(QueryNode.class);
    when(node.config()).thenReturn(config);
    query_context = mock(QueryContext.class);
    pipeline_context = mock(QueryPipelineContext.class);
    when(pipeline_context.queryContext()).thenReturn(query_context);
    when(query_context.query()).thenReturn(query);
    when(node.pipelineContext()).thenReturn(pipeline_context);
  }
}