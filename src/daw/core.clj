(ns daw.core
  (:import [javax.sound.sampled AudioSystem AudioFormat SourceDataLine DataLine$Info AudioFormat$Encoding]))

(def sample-rate 44100.0)
(def bpm 120)
(def frames-per-16th (int (/ (* sample-rate 60) bpm 4)))

(def pattern
  [{:sample :kick  :steps [1 0 0 0 1 0 0 0 1 0 0 0 1 0 0 0]}
   {:sample :snare :steps [0 0 0 0 1 0 0 0 0 0 0 0 1 0 0 0]}
   {:sample :hh    :steps [1 0 1 0 1 0 1 0 1 0 1 0 1 0 1 0]}
   {:sample :clap  :steps [0 0 0 0 0 0 0 0 0 0 0 0 0 0 1 1]}])

(def output-format (AudioFormat. sample-rate 16 2 true false))

(defn load-sample [path]
  (let [stream (AudioSystem/getAudioInputStream (java.io.File. path))
        src-format (.getFormat stream)
        target-format (AudioFormat. AudioFormat$Encoding/PCM_SIGNED
                                    (.getSampleRate src-format)
                                    16
                                    (.getChannels src-format)
                                    (* 2 (.getChannels src-format))
                                    (.getSampleRate src-format)
                                    false)
        converted (AudioSystem/getAudioInputStream target-format stream)
        bytes (byte-array (.available converted))]
    (.read converted bytes)
    (.close converted)
    {:bytes bytes :channels (.getChannels src-format)}))

(defn sample->stereo-ints [{:keys [bytes channels]}]
  (let [n-samples (/ (alength bytes) 2)
        n-frames (/ n-samples channels)
        out (int-array (* 2 n-frames))]
    (dotimes [i n-frames]
      (let [left (+ (bit-and (aget bytes (* i channels 2)) 0xFF)
                    (bit-shift-left (aget bytes (+ (* i channels 2) 1)) 8))]
        (aset out (* 2 i) left)
        (aset out (+ (* 2 i) 1) (if (= channels 2)
                                   (+ (bit-and (aget bytes (+ (* i channels 2) 2)) 0xFF)
                                      (bit-shift-left (aget bytes (+ (* i channels 2) 3)) 8))
                                   left))))
    out))

(defn ints->bytes [^ints arr]
  (let [out (byte-array (* 2 (alength arr)))]
    (dotimes [i (alength arr)]
      (let [v (aget arr i)]
        (aset out (* 2 i) (unchecked-byte (bit-and v 0xFF)))
        (aset out (+ (* 2 i) 1) (unchecked-byte (bit-shift-right v 8)))))
    out))

(defn mix-sample [^ints buffer ^ints sample]
  (let [len (min (alength sample) (alength buffer))]
    (dotimes [i len]
      (let [mixed (+ (aget buffer i) (aget sample i))]
        (aset buffer i (int (max -32768 (min 32767 mixed))))))))

(defn -main []
  (let [info (DataLine$Info. SourceDataLine output-format)
        line (AudioSystem/getLine info)
        samples {:kick  (sample->stereo-ints (load-sample "samples/BD Kick 006 HC.wav"))
                 :snare (sample->stereo-ints (load-sample "samples/SN Sd 4Bit Vinyl St GB.wav"))
                 :hh    (sample->stereo-ints (load-sample "samples/HH 60S Stomp2 GB.wav"))
                 :clap  (sample->stereo-ints (load-sample "samples/CL Claptrap 05 Mpc60 St GB.wav"))}
        samples-per-16th (* frames-per-16th 2)]
    (.open ^SourceDataLine line output-format)
    (.start ^SourceDataLine line)
    (println "Playing 4/4 (Ctrl+C to stop)")
    (loop [step 0]
      (let [buffer (int-array samples-per-16th)
            idx (mod step 16)]
        (doseq [{:keys [sample steps]} pattern]
          (when (= 1 (nth steps idx))
            (mix-sample buffer (get samples sample))))
        (let [out (ints->bytes buffer)]
          (.write ^SourceDataLine line out 0 (alength out))))
      (recur (inc step)))))
