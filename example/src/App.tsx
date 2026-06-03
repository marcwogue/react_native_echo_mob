import { Text, View, StyleSheet } from 'react-native';
import { multiply, getDayGreeting } from 'react-native-echo-mob';

const result = multiply(3, 7);
const greeting = getDayGreeting(1);

export default function App() {
  return (
    <View style={styles.container}>
      <Text>Result: {result}</Text>
      <Text>Greeting: {greeting}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
