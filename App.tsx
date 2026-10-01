import { useCallback, useEffect, useState } from "react";
import {
  AppState,
  Modal,
  PermissionsAndroid,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from "react-native";
import { StatusBar } from "expo-status-bar";
import * as DocumentPicker from "expo-document-picker";
import { DateTimePickerAndroid } from "@react-native-community/datetimepicker";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import Native, { Alarm, Status } from "./modules/alarm-native";

const C = {
  night: "#12163A",
  card: "#1D2352",
  line: "#333A78",
  text: "#F3F1FF",
  muted: "#9AA0D0",
  signal: "#FFC93C",
  onSignal: "#1A1400",
  bad: "#FF7A7A",
};
const DAYS = ["S", "M", "T", "W", "T", "F", "S"];
const DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const SETUP: { key: keyof Status; title: string; why: string }[] = [
  { key: "exact", title: "Exact alarms", why: "Rings at the exact minute" },
  {
    key: "battery",
    title: "Unrestricted battery",
    why: "Stops Android from killing the alarm",
  },
  {
    key: "overlay",
    title: "Display over other apps",
    why: "Opens the puzzle even if the screen was off",
  },
  {
    key: "fullScreen",
    title: "Full-screen alerts",
    why: "Shows the puzzle on the lock screen",
  },
];

const fmt = (h: number, m: number) =>
  `${((h + 11) % 12) + 1}:${String(m).padStart(2, "0")} ${h < 12 ? "AM" : "PM"}`;
const daysText = (d: number) =>
  d === 0
    ? "Once"
    : d === 127
      ? "Every day"
      : DAY_NAMES.filter((_, i) => (d >> i) & 1).join(" ");
const blank = (): Alarm => ({
  id: Math.floor(Math.random() * 2_000_000_000) + 1,
  hour: 7,
  minute: 0,
  days: 0,
  enabled: true,
  label: "",
  sound: "",
  soundName: "",
});

export default function App() {
  return (
    <SafeAreaProvider>
      <Home />
    </SafeAreaProvider>
  );
}

function Home() {
  const [alarms, setAlarms] = useState<Alarm[]>([]);
  const [status, setStatus] = useState<Status | null>(null);
  const [draft, setDraft] = useState<Alarm | null>(null);
  const [isNew, setIsNew] = useState(false);

  const refresh = useCallback(async () => {
    setAlarms(await Native.getAlarms());
    setStatus(await Native.getStatus());
  }, []);

  useEffect(() => {
    if (Platform.OS === "android" && Platform.Version >= 33)
      PermissionsAndroid.request("android.permission.POST_NOTIFICATIONS");
    refresh();
    const sub = AppState.addEventListener(
      "change",
      (s) => s === "active" && refresh(),
    );
    return () => sub.remove();
  }, [refresh]);

  const persist = async (next: Alarm[]) => {
    const sorted = [...next].sort(
      (a, b) => a.hour * 60 + a.minute - (b.hour * 60 + b.minute),
    );
    setAlarms(sorted);
    await Native.saveAlarms(sorted);
  };

  const pickTime = () => {
    if (!draft) return;
    const value = new Date();
    value.setHours(draft.hour, draft.minute, 0, 0);
    DateTimePickerAndroid.open({
      value,
      mode: "time",
      is24Hour: false,
      onChange: (e, d) =>
        e.type === "set" &&
        d &&
        setDraft(
          (p) =>
            p && {
              ...p,
              hour: d.getHours(),
              minute: d.getMinutes(),
              enabled: true,
            },
        ),
    });
  };

  const pickSound = async () => {
    const r = await DocumentPicker.getDocumentAsync({
      type: "audio/*",
      copyToCacheDirectory: false,
    });
    if (r.canceled || !draft) return;
    const f = r.assets[0];
    const path = await Native.copyAudio(f.uri);
    setDraft({ ...draft, sound: path, soundName: f.name });
  };

  const save = async () => {
    if (!draft) return;
    const rest = alarms.filter((a) => a.id !== draft.id);
    await persist([...rest, { ...draft, enabled: true }]);
    setDraft(null);
  };

  const missing = status ? SETUP.filter((s) => !status[s.key]) : [];

  return (
    <SafeAreaView style={s.root}>
      <StatusBar style="light" />
      <ScrollView contentContainerStyle={s.scroll}>
        <Text style={s.title}>Utho</Text>
        <Text style={s.sub}>
          Solve a sum to stop the alarm. No other way out.
        </Text>

        {missing.length > 0 && (
          <View style={s.setup}>
            <Text style={s.setupHead}>
              Allow these so the alarm can't be killed
            </Text>
            {missing.map((m) => (
              <View key={m.key} style={s.setupRow}>
                <View style={{ flex: 1 }}>
                  <Text style={s.setupTitle}>{m.title}</Text>
                  <Text style={s.muted}>{m.why}</Text>
                </View>
                <Pressable
                  style={s.pill}
                  onPress={() => Native.openSetting(m.key)}
                >
                  <Text style={s.pillText}>Allow</Text>
                </Pressable>
              </View>
            ))}
            <Text style={[s.muted, { marginTop: 8 }]}>
              On Xiaomi, Oppo, Vivo, Realme or Samsung, also enable Autostart
              and set battery to "No restrictions" in app settings.
            </Text>
            <Pressable onPress={() => Native.openSetting("app")}>
              <Text style={s.link}>Open app settings</Text>
            </Pressable>
          </View>
        )}

        {alarms.length === 0 && (
          <Text style={[s.muted, { marginTop: 40, textAlign: "center" }]}>
            No alarms yet. Add one and choose your own sound.
          </Text>
        )}

        {alarms.map((a) => (
          <Pressable
            key={a.id}
            style={s.card}
            onPress={() => {
              setDraft(a);
              setIsNew(false);
            }}
          >
            <View style={{ flex: 1 }}>
              <Text style={[s.time, !a.enabled && { color: C.muted }]}>
                {fmt(a.hour, a.minute)}
              </Text>
              <Text style={s.muted}>
                {daysText(a.days)}
                {a.label ? `  |  ${a.label}` : ""}
              </Text>
              <Text style={s.muted} numberOfLines={1}>
                {a.soundName ? a.soundName : "Default alarm tone"}
              </Text>
            </View>
            <Switch
              value={a.enabled}
              trackColor={{ true: C.signal, false: C.line }}
              thumbColor={a.enabled ? C.onSignal : C.muted}
              onValueChange={(v) =>
                persist(
                  alarms.map((x) => (x.id === a.id ? { ...x, enabled: v } : x)),
                )
              }
            />
          </Pressable>
        ))}
      </ScrollView>

      <Pressable
        style={s.fab}
        onPress={() => {
          setDraft(blank());
          setIsNew(true);
        }}
      >
        <Text style={s.fabText}>New alarm</Text>
      </Pressable>

      <Modal
        visible={!!draft}
        animationType="slide"
        onRequestClose={() => setDraft(null)}
      >
        {draft && (
          <SafeAreaView style={s.root}>
            <ScrollView
              contentContainerStyle={s.scroll}
              keyboardShouldPersistTaps="handled"
            >
              <Text style={s.editTitle}>
                {isNew ? "New alarm" : "Edit alarm"}
              </Text>

              <Pressable onPress={pickTime}>
                <Text style={s.bigTime}>{fmt(draft.hour, draft.minute)}</Text>
                <Text style={[s.muted, { textAlign: "center" }]}>
                  Tap to change time
                </Text>
              </Pressable>

              <Text style={s.sectionLabel}>Repeat</Text>
              <View style={s.dayRow}>
                {DAYS.map((d, i) => {
                  const on = (draft.days >> i) & 1;
                  return (
                    <Pressable
                      key={i}
                      style={[s.day, on ? s.dayOn : null]}
                      onPress={() =>
                        setDraft({ ...draft, days: draft.days ^ (1 << i) })
                      }
                    >
                      <Text
                        style={[s.dayText, on ? { color: C.onSignal } : null]}
                      >
                        {d}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
              <Text style={s.muted}>{daysText(draft.days)}</Text>

              <Text style={s.sectionLabel}>Label</Text>
              <TextInput
                style={s.input}
                value={draft.label}
                placeholder="Fajr, class, gym..."
                placeholderTextColor={C.muted}
                onChangeText={(t) => setDraft({ ...draft, label: t })}
              />

              <Text style={s.sectionLabel}>Sound</Text>
              <Pressable style={s.soundBox} onPress={pickSound}>
                <Text style={s.setupTitle} numberOfLines={1}>
                  {draft.soundName || "Default alarm tone"}
                </Text>
                <Text style={s.link}>Choose audio from files</Text>
              </Pressable>
              {!!draft.sound && (
                <Pressable
                  onPress={() =>
                    setDraft({ ...draft, sound: "", soundName: "" })
                  }
                >
                  <Text style={s.link}>Use default tone instead</Text>
                </Pressable>
              )}

              <Pressable
                style={s.ghost}
                onPress={() => Native.testRing(draft.sound)}
              >
                <Text style={s.ghostText}>Test ring now</Text>
              </Pressable>

              <Pressable style={s.primary} onPress={save}>
                <Text style={s.primaryText}>Save alarm</Text>
              </Pressable>
              {!isNew && (
                <Pressable
                  style={{ padding: 16 }}
                  onPress={async () => {
                    await persist(alarms.filter((a) => a.id !== draft.id));
                    setDraft(null);
                  }}
                >
                  <Text style={[s.link, { color: C.bad, textAlign: "center" }]}>
                    Delete alarm
                  </Text>
                </Pressable>
              )}
              <Pressable style={{ padding: 8 }} onPress={() => setDraft(null)}>
                <Text style={[s.muted, { textAlign: "center" }]}>Cancel</Text>
              </Pressable>
            </ScrollView>
          </SafeAreaView>
        )}
      </Modal>
    </SafeAreaView>
  );
}

const s = StyleSheet.create({
  root: { flex: 1, backgroundColor: C.night },
  scroll: { padding: 20, paddingBottom: 120 },
  title: {
    color: C.signal,
    fontSize: 44,
    fontWeight: "800",
    fontFamily: "sans-serif-condensed",
  },
  sub: { color: C.muted, fontSize: 15, marginBottom: 20 },
  muted: { color: C.muted, fontSize: 14 },
  setup: {
    borderWidth: 2,
    borderColor: C.signal,
    borderRadius: 16,
    padding: 16,
    marginBottom: 16,
  },
  setupHead: {
    color: C.text,
    fontSize: 16,
    fontWeight: "700",
    marginBottom: 8,
  },
  setupRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 8,
    gap: 12,
  },
  setupTitle: { color: C.text, fontSize: 16, fontWeight: "600" },
  pill: {
    backgroundColor: C.signal,
    borderRadius: 20,
    paddingHorizontal: 18,
    paddingVertical: 10,
    minHeight: 44,
    justifyContent: "center",
  },
  pillText: { color: C.onSignal, fontWeight: "700" },
  link: {
    color: C.signal,
    fontSize: 15,
    fontWeight: "600",
    paddingVertical: 8,
  },
  card: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: C.card,
    borderRadius: 20,
    padding: 18,
    marginBottom: 12,
    gap: 12,
  },
  time: {
    color: C.text,
    fontSize: 40,
    fontWeight: "800",
    fontFamily: "sans-serif-condensed",
  },
  fab: {
    position: "absolute",
    left: 20,
    right: 20,
    bottom: 24,
    backgroundColor: C.signal,
    borderRadius: 28,
    height: 56,
    alignItems: "center",
    justifyContent: "center",
  },
  fabText: { color: C.onSignal, fontSize: 18, fontWeight: "800" },
  editTitle: {
    color: C.text,
    fontSize: 22,
    fontWeight: "700",
    marginBottom: 12,
  },
  bigTime: {
    color: C.signal,
    fontSize: 72,
    fontWeight: "800",
    textAlign: "center",
    fontFamily: "sans-serif-condensed",
  },
  sectionLabel: {
    color: C.text,
    fontSize: 16,
    fontWeight: "700",
    marginTop: 24,
    marginBottom: 10,
  },
  dayRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginBottom: 6,
  },
  day: {
    width: 44,
    height: 44,
    borderRadius: 22,
    borderWidth: 2,
    borderColor: C.line,
    alignItems: "center",
    justifyContent: "center",
  },
  dayOn: { backgroundColor: C.signal, borderColor: C.signal },
  dayText: { color: C.text, fontWeight: "700" },
  input: {
    backgroundColor: C.card,
    color: C.text,
    borderRadius: 14,
    padding: 14,
    fontSize: 16,
  },
  soundBox: { backgroundColor: C.card, borderRadius: 14, padding: 14 },
  ghost: {
    borderWidth: 2,
    borderColor: C.line,
    borderRadius: 28,
    height: 52,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 28,
  },
  ghostText: { color: C.text, fontWeight: "700", fontSize: 16 },
  primary: {
    backgroundColor: C.signal,
    borderRadius: 28,
    height: 56,
    alignItems: "center",
    justifyContent: "center",
    marginTop: 14,
  },
  primaryText: { color: C.onSignal, fontSize: 18, fontWeight: "800" },
});
