package com.graphhopper.storage.change;

import com.graphhopper.json.GHJson;
import com.graphhopper.json.GHJsonFactory;
import com.graphhopper.json.JsonFeatureConverter;
import com.graphhopper.routing.AbstractRoutingAlgorithmTester;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.DecimalEncodedValue;
import com.graphhopper.routing.profiles.TagParserFactory;
import com.graphhopper.routing.util.AllEdgesIterator;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.storage.RAMDirectory;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.GHUtility;
import com.graphhopper.util.Helper;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;

import static org.junit.Assert.*;

/**
 * @author Peter Karich
 */
public class ChangeGraphHelperTest {
    private EncodingManager encodingManager;
    private GraphHopperStorage graph;
    private GHJson ghson;

    @Before
    public void setUp() {
        encodingManager = new EncodingManager.Builder().addAllFlagEncoders("car").build();
        graph = new GraphBuilder(encodingManager).create();
        ghson = new GHJsonFactory().create();
    }

    @Test
    public void testApplyChanges() {
        FlagEncoder encoder = encodingManager.getEncoder("car");
        BooleanEncodedValue accessEnc = encodingManager.getBooleanEncodedValue(TagParserFactory.Car.ACCESS);
        DecimalEncodedValue avSpeedEnc = encodingManager.getDecimalEncodedValue(TagParserFactory.Car.AVERAGE_SPEED);


        // 0-1-2
        // | |
        // 3-4
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 1, true, 1);
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 1, 2, true, 1);
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 3, 4, true, 1);
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 0, 3, true, 1);
        GHUtility.createEdge(graph, avSpeedEnc, 60, accessEnc, 1, 4, true, 1);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 0, 0.01, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 1, 0.01, 0.01);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 2, 0.01, 0.02);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 3, 0.00, 0.00);
        AbstractRoutingAlgorithmTester.updateDistancesFor(graph, 4, 0.00, 0.01);
        LocationIndex locationIndex = new LocationIndexTree(graph, new RAMDirectory()).prepareIndex();

        double defaultSpeed = encoder.getSpeed(GHUtility.getEdge(graph, 0, 1).getData());
        AllEdgesIterator iter = graph.getAllEdges();
        while (iter.next()) {
            IntsRef flags = GHUtility.getEdge(graph, 0, 1).getData();
            assertEquals(defaultSpeed, encoder.getSpeed(flags), .1);
            assertTrue(accessEnc.getBool(false, flags));
        }

        Reader reader = new InputStreamReader(getClass().getResourceAsStream("overlaydata1.json"), Helper.UTF_CS);
        ChangeGraphHelper instance = new ChangeGraphHelper(graph, locationIndex);
        JsonFeatureConverter converter = new JsonFeatureConverter(ghson, instance, encodingManager);
        long updates = converter.applyChanges(reader);
        assertEquals(2, updates);

        // assert changed speed and access
        double newSpeed = encoder.getSpeed(GHUtility.getEdge(graph, 0, 1).getData());
        assertEquals(10, newSpeed, .1);
        assertTrue(newSpeed < defaultSpeed);
        assertFalse(GHUtility.getEdge(graph, 3, 4).get(accessEnc));
    }
}
