// The location task must be defined before anything else runs, including when Android starts
// the JS bundle without UI to deliver a location update.
import './src/tracking/MeterService';
import 'expo-router/entry';
