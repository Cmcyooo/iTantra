#!/usr/bin/env python3
"""
Corpus & Noise Generator for Multilingual STT Benchmarking.
Generates:
1. Standardized 30-utterance set per language across 10 target languages:
   - 10 Short utterances (1–3s): tactical alerts, checks, rogers
   - 10 Medium utterances (3–7s): coordinates, perimeter reports, numbers
   - 10 Long utterances (7–15s): multi-clause tactical field dispatches
2. Clean speech audio set.
3. Moderate-noise audio set (15 dB SNR calibrated ambient/radio noise).
"""

import os
import sys
import json
import shutil
from pathlib import Path
import numpy as np
import soundfile as sf
import sherpa_onnx

ROOT_DIR = Path(__file__).resolve().parent.parent.parent
STT_ACC_DIR = ROOT_DIR / "benchmarks" / "stt_accuracy"
EXISTING_DATASET = STT_ACC_DIR / "test_dataset_all_10_languages.json"
EXISTING_AUDIO_DIR = STT_ACC_DIR / "audio"

CORPUS_DIR = ROOT_DIR / "benchmarks" / "stt" / "corpus"
CLEAN_DIR = CORPUS_DIR / "clean"
NOISY_DIR = CORPUS_DIR / "noisy"
OUTPUT_JSON = CORPUS_DIR / "test_dataset_30_per_lang.json"

TTS_MODELS_DIR = ROOT_DIR / "benchmarks" / "tts" / "models"
APP_ASSETS_DIR = ROOT_DIR / "app" / "src" / "main" / "assets"

VOICE_CONFIGS = {
    "en": {"dir": APP_ASSETS_DIR / "tts-en-amy", "is_piper": True},
    "hi": {"dir": TTS_MODELS_DIR / "piper_hi_priyamvada", "is_piper": True},
    "mr": {"dir": TTS_MODELS_DIR / "piper_mr_google", "is_piper": True},
    "bn": {"dir": TTS_MODELS_DIR / "piper_bn_google", "is_piper": True},
    "te": {"dir": TTS_MODELS_DIR / "piper_te_maya", "is_piper": True},
    "ml": {"dir": TTS_MODELS_DIR / "piper_ml_meera", "is_piper": True},
    "ta": {"dir": TTS_MODELS_DIR / "piper_ta_rasa_female", "is_piper": True},
    "gu": {"dir": TTS_MODELS_DIR / "mms_guj", "is_piper": False},
    "kn": {"dir": TTS_MODELS_DIR / "mms_kan", "is_piper": False},
    "or": {"dir": TTS_MODELS_DIR / "mms_ory", "is_piper": False},
}

LONG_UTTERANCES = {
    "en": [
        {"id": "en_21_long_tactical_1", "text": "convoy alpha one reporting to base bravo western perimeter fence breached near grid coordinate forty two requesting immediate medical evacuation and secondary support team over"},
        {"id": "en_22_long_tactical_2", "text": "station delta confirms oxygen level dropping to thirty percent in sector three all personnel evacuate toward southern exit gate and report headcount immediately"},
        {"id": "en_23_long_tactical_3", "text": "search and rescue team twelve moving into flooded district water level rising approximately two meters above street grade deploying inflatable boats now"},
        {"id": "en_24_long_tactical_4", "text": "emergency alert severe weather warning approaching coastal sector seven wind speeds exceeding seventy kilometers per hour secure all equipment and stand by"},
        {"id": "en_25_long_tactical_5", "text": "command center to patrol units seven and eight proceed along western highway junction four maintain radio silence until reaching staging post charlie"},
        {"id": "en_26_long_tactical_6", "text": "medical supply convoy carrying emergency rations and first aid kits has arrived at relief camp nine distribution beginning under supervision"},
        {"id": "en_27_long_tactical_7", "text": "perimeter reconnaissance reports power failure at relay tower three backup generators operational communication restored at standard frequency"},
        {"id": "en_28_long_tactical_8", "text": "tactical squad bravo moving through forest corridor toward waypoint five encounter zero hostile activity proceeding under normal operational protocol"},
        {"id": "en_29_long_tactical_9", "text": "engineering team reports structural damage to bridge across river northern span unsafe for heavy vehicles redirecting all traffic east"},
        {"id": "en_30_long_tactical_10", "text": "headquarters confirms all primary checkpoints clear and operational night patrol shifts take positions immediately and report status every thirty minutes over"}
    ],
    "hi": [
        {"id": "hi_21_long_tactical_1", "text": "काफिला अल्फा एक बेस ब्रावो को सूचित करता है कि ग्रिड समन्वय बयालीस के पास पश्चिमी बाड़ टूट गई है और तुरंत चिकित्सा दल की आवश्यकता है"},
        {"id": "hi_22_long_tactical_2", "text": "स्टेशन डेल्टा पुष्टि करता है कि सेक्टर तीन में ऑक्सीजन का स्तर तीस प्रतिशत तक गिर गया है सभी कर्मी दक्षिणी निकास द्वार की ओर बढ़ें"},
        {"id": "hi_23_long_tactical_3", "text": "खोज और बचाव दल बारह बाढ़ प्रभावित क्षेत्र में आगे बढ़ रहा है पानी का स्तर सड़क से दो मीटर ऊपर पहुंच चुका है नौकाएं तैनात हैं"},
        {"id": "hi_24_long_tactical_4", "text": "आपातकालीन चेतावनी तटीय सेक्टर सात में भीषण तूफान आ रहा है हवा की गति सत्तर किलोमीटर प्रति घंटा है सभी उपकरण सुरक्षित करें"},
        {"id": "hi_25_long_tactical_5", "text": "कमांड सेंटर सभी गश्ती दलों को निर्देश देता है कि पश्चिमी राजमार्ग जंक्शन चार पर पहुंचें और रेडियो संपर्क बनाए रखें"},
        {"id": "hi_26_long_tactical_6", "text": "चिकित्सा आपूर्ति काफिला राहत शिविर नौ में पहुंच चुका है भोजन और प्राथमिक उपचार सामग्री का वितरण शुरू किया जा रहा है"},
        {"id": "hi_27_long_tactical_7", "text": "निगरानी दल ने रिले टावर तीन पर बिजली आपूर्ति बाधित होने की सूचना दी है बैकअप जनरेटर चालू कर दिए गए हैं"},
        {"id": "hi_28_long_tactical_8", "text": "दस्ता ब्रावो वन क्षेत्र से होते हुए बिंदु पाँच की ओर बढ़ रहा है कोई संदिग्ध गतिविधि नहीं देखी गई है"},
        {"id": "hi_29_long_tactical_9", "text": "अभियांत्रिकी टीम ने नदी के पुल को भारी वाहनों के लिए असुरक्षित घोषित किया है सभी यातायात को पूर्व दिशा में मोड़ा गया है"},
        {"id": "hi_30_long_tactical_10", "text": "मुख्यालय पुष्टि करता है कि सभी मुख्य चौकियां सुरक्षित हैं रात्रि गश्ती दल तुरंत अपना स्थान लें और हर आधे घंटे में रिपोर्ट करें"}
    ],
    "mr": [
        {"id": "mr_21_long_tactical_1", "text": "तुकडी अल्फा एक बेस ब्राव्होला कळवते की ग्रिड समन्वय बेचाळीस जवळ पश्चिम कुंपण तुटले आहे आणि तातडीने वैद्यकीय मदतीची गरज आहे"},
        {"id": "mr_22_long_tactical_2", "text": "स्टेशन डेल्टा खात्री करतो की सेक्टर तीनमध्ये ऑक्सिजन तीस टक्क्यांपर्यंत कमी झाला आहे सर्व कर्मचाऱ्यांनी दक्षिण गेटकडे जावे"},
        {"id": "mr_23_long_tactical_3", "text": "शोध आणि बचाव पथक बारा पूरग्रस्त भागात पुढे सरकत आहे पाण्याचा स्तर दोन मीटर वाढला असून बोटी तैनात केल्या आहेत"},
        {"id": "mr_24_long_tactical_4", "text": "तातडीचा इशारा किनारपट्टीच्या सेक्टर सातवर वादळ धडकणार आहे वाऱ्याचा वेग सत्तर किलोमीटर प्रति तास असून सर्व उपकरणे सुरक्षित ठेवा"},
        {"id": "mr_25_long_tactical_5", "text": "कमांड सेंटर सर्व गस्त पथकांना पश्चिम महामार्ग जंक्शन चारकडे जाण्याचे आणि रेडिओ संपर्क चालू ठेवण्याचे आदेश देते"},
        {"id": "mr_26_long_tactical_6", "text": "वैद्यकीय मदत पुरवठा करणारी वाहने मदत छावणी नऊ येथे पोहोचली असून अन्न आणि औषधांचे वाटप सुरू झाले आहे"},
        {"id": "mr_27_long_tactical_7", "text": "सुरक्षा पथकाने रिले टॉवर तीनचा वीजपुरवठा खंडित झाल्याचे सांगितले आहे जनरेटर सुरू करून संपर्क पूर्ववत केला आहे"},
        {"id": "mr_28_long_tactical_8", "text": "तुकडी ब्राव्हो जंगलातून मार्ग काढत पॉईंट पाचकडे जात असून कोणतीही अडचण नाही सुरळीत काम चालू आहे"},
        {"id": "mr_29_long_tactical_9", "text": "अभियांत्रिकी पथकाने नदीवरील पूल अवजड वाहनांसाठी धोकादायक ठरवला असून सर्व वाहतूक पूर्वेकडे वळवली आहे"},
        {"id": "mr_30_long_tactical_10", "text": "मुख्यालय खात्री करते की सर्व मुख्य चौक्या सुरक्षित आहेत रात्र गस्त पथकांनी त्वरित जागा घ्यावी आणि दर अर्ध्या तासाने अहवाल द्यावा"}
    ],
    "gu": [
        {"id": "gu_21_long_tactical_1", "text": "કાફલો આલ્ફા એક બેઝ બ્રાવોને જણાવે છે કે ગ્રીડ સમન્વય બેતાલીસ પાસે પશ્ચિમ વાડ તૂટી ગઈ છે અને તાત્કાલિક તબીબી સહાયની જરૂર છે"},
        {"id": "gu_22_long_tactical_2", "text": "સ્ટેશન ડેલ્ટા પુષ્ટિ કરે છે કે સેક્ટર ત્રણમાં ઓક્સિજન ત્રીસ ટકા સુધી ઘટી ગયું છે તમામ સભ્યો દક્ષિણ દરવાજા તરફ આગળ વધો"},
        {"id": "gu_23_long_tactical_3", "text": "શોધ અને બચાવ ટીમ બાર પૂરગ્રસ્ત વિસ્તારમાં પહોંચી રહી છે પાણીની સપાટી બે મીટર વધી છે અને બોટ તૈનાત કરવામાં આવી છે"},
        {"id": "gu_24_long_tactical_4", "text": "કટોકટી ચેતવણી દરિયાકાંઠાના સેક્ટર સાતમાં મોટું વાવાઝોડું આવી રહ્યું છે પવનની ગતિ સિત્તેર કિલોમીટર પ્રતિ કલાક છે સાવચેત રહો"},
        {"id": "gu_25_long_tactical_5", "text": "કમાન્ડ સેન્ટર તમામ ટીમોને પશ્ચિમ હાઇવે જંકશન ચાર તરફ આગળ વધવા અને રેડિયો સંપર્કમાં રહેવા આદેશ આપે છે"},
        {"id": "gu_26_long_tactical_6", "text": "તબીબી સામગ્રી ભરેલો કાફલો રાહત શિબિર નવમાં પહોંચ્યો છે ભોજન અને પ્રાથમિક સારવારનું વિતરણ શરૂ થયું છે"},
        {"id": "gu_27_long_tactical_7", "text": "સુરક્ષા ટીમે રિલે ટાવર ત્રણ પર વીજળી બંધ થવાની માહિતી આપી છે બેકઅપ જનરેટર ચાલુ કરી સંપર્ક પુનઃસ્થાપિત કરાયો છે"},
        {"id": "gu_28_long_tactical_8", "text": "દળ બ્રાવો જંગલ માર્ગે ચેકપોઇન્ટ પાંચ તરફ આગળ વધી રહ્યું છે પરિસ્થિતિ સામાન્ય અને નિયંત્રણ હેઠળ છે"},
        {"id": "gu_29_long_tactical_9", "text": "ઇજનેરી ટીમે નદીનો પુલ ભારે વાહનો માટે જોખમી જાહેર કર્યો છે અને સમગ્ર ટ્રાફિક પૂર્વ તરફ વાળ્યો છે"},
        {"id": "gu_30_long_tactical_10", "text": "મુખ્યાલય પુષ્ટિ કરે છે કે તમામ મુખ્ય ચેકપોસ્ટ સુરક્ષિત છે રાત્રિ પેટ્રોલિંગ ટુકડી તુરંત ફરજ પર હાજર થાય"}
    ],
    "bn": [
        {"id": "bn_21_long_tactical_1", "text": "কনভয় আলফা এক বেস ব্রাভোকে জানাচ্ছে গ্রিড কোঅর্ডিনেট বিয়াল্লিশের কাছে পশ্চিম সীমানা ভেঙে পড়েছে অবিলম্বে চিকিৎসা সাহায্য প্রয়োজন"},
        {"id": "bn_22_long_tactical_2", "text": "স্টেশন ডেল্টা নিশ্চিত করছে তিন নম্বর সেক্টরে অক্সিজেনের মাত্রা ত্রিশ শতাংশে নেমে এসেছে সকল কর্মী দক্ষিণ গেটের দিকে যান"},
        {"id": "bn_23_long_tactical_3", "text": "অনুসন্ধান ও উদ্ধারকারী দল বারো বন্যা কবলিত অঞ্চলে এগিয়ে চলেছে জলস্তর দুই মিটার বৃদ্ধি পেয়েছে নৌকা মোতায়েন করা হয়েছে"},
        {"id": "bn_24_long_tactical_4", "text": "জরুরী সতর্কতা উপকূলীয় সেক্টর সাতে তীব্র ঝড় আঘাত হানতে চলেছে বাতাসের গতি সত্তর কিলোমিটার প্রতি ঘণ্টা সকলে প্রস্তুত থাকুন"},
        {"id": "bn_25_long_tactical_5", "text": "কমান্ড সেন্টার সকল টহল দলকে পশ্চিম হাইওয়ে জংশন চারে পৌঁছাতে এবং রেডিও যোগাযোগ বজায় রাখতে নির্দেশ দিচ্ছে"},
        {"id": "bn_26_long_tactical_6", "text": "চিকিৎসা ত্রাণবাহী কনভয় নয় নম্বর শিবিরে পৌঁছেছে খাদ্য এবং প্রাথমিক চিকিৎসা সামগ্রী বিতরণ শুরু করা হচ্ছে"},
        {"id": "bn_27_long_tactical_7", "text": "সুরক্ষা দল জানিয়েছে রিলে টাওয়ার তিনে বিদ্যুৎ বিভ্রাট ঘটেছে ব্যাকআপ জেনারেটর চালু করে সংযোগ স্বাভাবিক করা হয়েছে"},
        {"id": "bn_28_long_tactical_8", "text": "ট্যাকটিক্যাল স্কোয়াড ব্রাভো জঙ্গলের পথ দিয়ে পাঁচ নম্বর পয়েন্টের দিকে এগোচ্ছে পরিস্থিতি সম্পূর্ণ শান্ত ও নিরাপদ"},
        {"id": "bn_29_long_tactical_9", "text": "প্রকৌশলী দল নদীর সেতুটিকে ভারী যানবাহনের জন্য বিপজ্জনক ঘোষণা করেছে সমস্ত যান চলাচল পূর্ব দিকে ঘুরিয়ে দেওয়া হয়েছে"},
        {"id": "bn_30_long_tactical_10", "text": "সদর দপ্তর নিশ্চিত করছে সমস্ত প্রধান চৌকি সুরক্ষিত রয়েছে রাতের টহল দল অবিলম্বে অবস্থান নিয়ে প্রতি আধ ঘণ্টায় রিপোর্ট দিন"}
    ],
    "te": [
        {"id": "te_21_long_tactical_1", "text": "కాన్వాయ్ ఆల్ఫా ఒకటి బేస్ బ్రావోకు నివేదిస్తుంది గ్రిడ్ సమన్వయం నలభై రెండు వద్ద పశ్చిమ సరిహద్దు కంచె దెబ్బతింది తక్షణ వైద్య సహాయం కావాలి"},
        {"id": "te_22_long_tactical_2", "text": "స్టేషన్ డెల్టా ధృవీకరిస్తుంది సెక్టార్ మూడులో ఆక్సిజన్ స్థాయి ముప్పై శాతానికి పడిపోయింది సిబ్బంది అంతా దక్షిణ ద్వారం వైపు వెళ్లండి"},
        {"id": "te_23_long_tactical_3", "text": "శోధన మరియు సహాయక బృందం పన్నెండు వరద ప్రాంతంలోకి చేరుకుంటుంది నీటి మట్టం రెండు మీటర్లు పెరిగింది పడవలు సిద్ధం చేశాము"},
        {"id": "te_24_long_tactical_4", "text": "అత్యవసర హెచ్చరిక తీరప్రాంత సెక్టార్ ఏడులో తీవ్రమైన తుఫాను వస్తుంది గాలి వేగం గంటకు డెబ్బై కిలోమీటర్లు అందరూ జాగ్రత్తగా ఉండండి"},
        {"id": "te_25_long_tactical_5", "text": "కమాండ్ సెంటర్ పెట్రోలింగ్ బృందాలకు పశ్చిమ రహదారి జంక్షన్ నాలుగు వద్దకు చేరుకోవాలని రేడియో సంప్రదింపుల్లో ఉండాలని ఆదేశించింది"},
        {"id": "te_26_long_tactical_6", "text": "వైద్య సామాగ్రి కాన్వాయ్ తొమ్మిదివ శిబిరానికి చేరుకుంది ఆహారం మరియు ప్రథమ చికిత్స కిట్లు పంపిణీ ప్రారంభమైంది"},
        {"id": "te_27_long_tactical_7", "text": "రిలే టవర్ మూడు వద్ద విద్యుత్ నిలిచిపోయిందని బృందం తెలిపింది జనరేటర్లు ప్రారంభించి కమ్యూనికేషన్ పునరుద్ధరించాము"},
        {"id": "te_28_long_tactical_8", "text": "బృందం బ్రావో అటవీ మార్గం గుండా పాయింట్ ఐదు వైపు కదులుతోంది ఎలాంటి అనుమానాస్పద కదలికలు లేవు అంతా సురక్షితం"},
        {"id": "te_29_long_tactical_9", "text": "నదిపై ఉన్న వంతెన భారీ వాహనాలకు సురక్షితం కాదని ఇంజనీరింగ్ బృందం తెలిపింది ట్రాఫిక్ మొత్తం తూర్పు వైపు మళ్లించాము"},
        {"id": "te_30_long_tactical_10", "text": "ప్రధాన చెక్‌పోస్టులన్నీ సురక్షితంగా ఉన్నాయని ప్రధాన కార్యాలయం నిర్ధారించింది రాత్రి గస్తీ బృందాలు వెంటనే స్థానాలను తీసుకోండి"}
    ],
    "kn": [
        {"id": "kn_21_long_tactical_1", "text": "ಕಾನ್ವಾಯ್ ಆಲ್ಫಾ ಒಂದು ಬೇಸ್ ಬ್ರಾವೋಗೆ ವರದಿ ಮಾಡುತ್ತದೆ ಗ್ರಿಡ್ ಸಮನ್ವಯ ನಲವತ್ತೆರಡರ ಬಳಿ ಪಶ್ಚಿಮ ಬೇಲಿ ಮುರಿದಿದೆ ತುರ್ತು ವೈದ್ಯಕೀಯ ನೆರವು ಬೇಕು"},
        {"id": "kn_22_long_tactical_2", "text": "ಸ್ಟೇಷನ್ ಡೆಲ್ಟಾ ದೃಢಪಡಿಸುತ್ತದೆ ಸೆಕ್ಟರ್ ಮೂರರಲ್ಲಿ ಆಮ್ಲಜನಕ ಮಟ್ಟ ಮೂವತ್ತು ಪ್ರತಿಶತಕ್ಕೆ ಇಳಿದಿದೆ ಎಲ್ಲಾ ಸಿಬ್ಬಂದಿ ದಕ್ಷಿಣ ಗೇಟ್‌ಗೆ ತೆರಳಿ"},
        {"id": "kn_23_long_tactical_3", "text": "ಶೋಧನೆ ಮತ್ತು ರಕ್ಷಣಾ ತಂಡ ಹನ್ನೆರಡು ಪ್ರವಾಹ ಪೀಡಿತ ಪ್ರದೇಶಕ್ಕೆ ಮುನ್ನಡೆಯುತ್ತಿದೆ ನೀರಿನ ಮಟ್ಟ ಎರಡು ಮೀಟರ್ ಏರಿಕೆಯಾಗಿದ್ದು ದೋಣಿ ನಿಯೋಜಿಸಲಾಗಿದೆ"},
        {"id": "kn_24_long_tactical_4", "text": "ತುರ್ತು ಎಚ್ಚರಿಕೆ ಕರಾವಳಿ ಸೆಕ್ಟರ್ ಏಳರಲ್ಲಿ ಭೀಕರ ಚಂಡಮಾರುತ ಅಪ್ಪಳಿಸುತ್ತಿದೆ ಗಾಳಿಯ ವೇಗ ಗಂಟೆಗೆ ಎಪ್ಪತ್ತು ಕಿಲೋಮೀಟರ್ ಜಾಗರೂಕರಾಗಿರಿ"},
        {"id": "kn_25_long_tactical_5", "text": "ಕಮಾಂಡ್ ಸೆಂಟರ್ ಎಲ್ಲಾ ಗಸ್ತು ತಂಡಗಳಿಗೆ ಪಶ್ಚಿಮ ಹೆದ್ದಾರಿ ಜಂಕ್ಷನ್ ನಾಲ್ಕಕ್ಕೆ ತಲುಪಲು ಮತ್ತು ರೇಡಿಯೋ ಸಂಪರ್ಕದಲ್ಲಿರಲು ಆದೇಶಿಸಿದೆ"},
        {"id": "kn_26_long_tactical_6", "text": "ವೈದ್ಯಕೀಯ ನೆರವು ಕಾನ್ವಾಯ್ ಒಂಬತ್ತನೇ ಪರಿಹಾರ ಶಿಬಿರಕ್ಕೆ ತಲುಪಿದೆ ಆಹಾರ ಮತ್ತು ಪ್ರಥಮ ಚಿಕಿತ್ಸಾ ಸಾಮಗ್ರಿ ವಿತರಣೆ ಆರಂಭವಾಗಿದೆ"},
        {"id": "kn_27_long_tactical_7", "text": "ರಿಲೇ ಟವರ್ ಮೂರರಲ್ಲಿ ವಿದ್ಯುತ್ ಸ್ಥಗಿತಗೊಂಡಿದೆ ಎಂದು ತಂಡ ತಿಳಿಸಿದೆ ಜನರೇಟರ್ ಚಾಲನೆ ಮಾಡಿ ಸಂಪರ್ಕ ಪುನಃಸ್ಥಾಪಿಸಲಾಗಿದೆ"},
        {"id": "kn_28_long_tactical_8", "text": "ತಂಡ ಬ್ರಾವೋ ಅರಣ್ಯ ಮಾರ್ಗದ ಮೂಲಕ ಪಾಯಿಂಟ್ ಐದಕ್ಕೆ ಚಲಿಸುತ್ತಿದೆ ಯಾವುದೇ ಅನುಮಾನಾಸ್ಪದ ಚಟುವಟಿಕೆ ಇಲ್ಲ ಎಲ್ಲವೂ ಸುರಕ್ಷಿತ"},
        {"id": "kn_29_long_tactical_9", "text": "ನದಿಯ ಸೇತುವೆ ಭಾರೀ ವಾಹನಗಳಿಗೆ ಸುರಕ್ಷಿತವಲ್ಲ ಎಂದು ಎಂಜಿನಿಯರಿಂಗ್ ತಂಡ ತಿಳಿಸಿದೆ ಸಂಚಾರವನ್ನು ಪೂರ್ವಕ್ಕೆ ತಿರುಗಿಸಲಾಗಿದೆ"},
        {"id": "kn_30_long_tactical_10", "text": "ಎಲ್ಲಾ ಪ್ರಮುಖ ಚೆಕ್‌ಪೋಸ್ಟ್‌ಗಳು ಸುರಕ್ಷಿತವಾಗಿವೆ ಎಂದು ಮುಖ್ಯ ಕಚೇರಿ ಖಚಿತಪಡಿಸಿದೆ ರಾತ್ರಿ ಗಸ್ತು ತಂಡಗಳು ತಕ್ಷಣ ಕರ್ತವ್ಯಕ್ಕೆ ಹಾಜರಾಗಿ"}
    ],
    "ml": [
        {"id": "ml_21_long_tactical_1", "text": "കോൺവോയ് ആൽഫ ഒന്ന് ബേസ് ബ്രാവോയെ അറിയിക്കുന്നു ഗ്രിഡ് കോർഡിനേറ്റ് നാൽപ്പത്തിരണ്ടിന് സമീപം പടിഞ്ഞാറൻ വേലി തകർന്നു അടിയന്തര ചികിത്സ സഹായം വേണം"},
        {"id": "ml_22_long_tactical_2", "text": "സ്റ്റേഷൻ ഡെൽറ്റ സ്ഥിരീകരിക്കുന്നു സെക്ടർ മൂന്നിൽ ഓക്സിജൻ നില മുപ്പത് ശതമാനമായി കുറഞ്ഞു എല്ലാ ജീവനക്കാരും തെക്കൻ ഗേറ്റിലേക്ക് മാറുക"},
        {"id": "ml_23_long_tactical_3", "text": "തിരച്ചിൽ രക്ഷാസംഘം പന്ത്രണ്ട് വെള്ളപ്പൊക്ക പ്രദേശത്തേക്ക് നീങ്ങുന്നു വെള്ളം രണ്ട് മീറ്റർ ഉയർന്നു ബോട്ടുകൾ സജ്ജമാക്കിയിട്ടുണ്ട്"},
        {"id": "ml_24_long_tactical_4", "text": "അടിയന്തര മുന്നറിയിപ്പ് തീരദേശ സെക്ടർ ഏഴിൽ കനത്ത കാറ്റും മഴയും വീശുന്നു കാറ്റിന്റെ വേഗത എഴുപത് കിലോമീറ്റർ സുരക്ഷിതമായി ഇരിക്കുക"},
        {"id": "ml_25_long_tactical_5", "text": "കമാൻഡ് സെന്റർ പട്രോളിംഗ് സംഘത്തോട് പടിഞ്ഞാറൻ ഹൈവേ ജംഗ്ഷൻ നാലിൽ എത്താനും റേഡിയോ ബന്ധം നിലനിർത്താനും നിർദ്ദേശിക്കുന്നു"},
        {"id": "ml_26_long_tactical_6", "text": "വൈദ്യസഹായ സംഘം ക്യാമ്പ് ഒമ്പതിൽ എത്തിച്ചേർന്നു ഭക്ഷണവും പ്രഥമശുശ്രൂഷാ സാമഗ്രികളും വിതരണം ചെയ്യാൻ തുടങ്ങി"},
        {"id": "ml_27_long_tactical_7", "text": "റിലേ ടവർ മൂന്നിൽ വൈദ്യുതി തടസ്സപ്പെട്ടു എന്ന് റിപ്പോർട്ട് ബാക്കപ്പ് ജനറേറ്റർ പ്രവർത്തിപ്പിച്ച് ബന്ധം പുനഃസ്ഥാപിച്ചു"},
        {"id": "ml_28_long_tactical_8", "text": "സൈനിക സംഘം വനപാതയിലൂടെ പോയിന്റ് അഞ്ചിലേക്ക് നീങ്ങുന്നു സംശയാസ്പദമായ ഒന്നും കണ്ടില്ല എല്ലാം സുരക്ഷിതമാണ്"},
        {"id": "ml_29_long_tactical_9", "text": "നദിക്ക് കുറുകെയുള്ള പാലം വലിയ വാഹനങ്ങൾക്ക് സുരക്ഷിതമല്ലെന്ന് എൻജിനീയറിങ് ടീം അറിയിച്ചു ഗതാഗതം കിഴക്കോട്ട് തിരിച്ചുവിട്ടു"},
        {"id": "ml_30_long_tactical_10", "text": "എല്ലാ പ്രധാന ചെക്ക്‌പോസ്റ്റുകളും സുരക്ഷിതമാണെന്ന് ആസ്ഥാനത്ത് സ്ഥിരീകരിച്ചു രാത്രി പട്രോളിംഗ് സംഘം ഉടൻ ചുമതല ഏൽക്കുക"}
    ],
    "ta": [
        {"id": "ta_21_long_tactical_1", "text": "வாகன அணிவகுப்பு ஆல்பா ஒன்று தளம் பிராவோவுக்கு தெரிவிக்கிறது கட்டம் ஒருங்கிணைப்பு நாற்பத்திரண்டு அருகே மேற்கு வேலி உடைந்தது அவசர மருத்துவ உதவி தேவை"},
        {"id": "ta_22_long_tactical_2", "text": "நிலையம் டெல்டா உறுதிப்படுத்துகிறது பிரிவு மூன்றில் ஆக்ஸிஜன் அளவு முப்பது சதவீதமாக குறைந்துள்ளது அனைத்து ஊழியர்களும் தெற்கு வாயிலுக்கு செல்லவும்"},
        {"id": "ta_23_long_tactical_3", "text": "தேடுதல் மீட்புக் குழு பன்னிரண்டு வெள்ளப் பகுதிக்கு செல்கிறது நீர்மட்டம் இரண்டு மீட்டர் உயர்ந்துள்ளது படகுகள் நிலைநிறுத்தப்பட்டுள்ளன"},
        {"id": "ta_24_long_tactical_4", "text": "அவசர எச்சரிக்கை கடலோரப் பிரிவு ஏழில் கடுமையான புயல் வீசுகிறது காற்றின் வேகம் எழுபது கிலோமீட்டர் அனைவரும் பாதுகாப்பாக இருக்கவும்"},
        {"id": "ta_25_long_tactical_5", "text": "கட்டளை மையம் ரோந்து குழுக்களை மேற்கு நெடுஞ்சாலை சந்திப்பு நான்கிற்கு சென்று வானொலி தொடர்பில் இருக்குமாறு உத்தரவிடுகிறது"},
        {"id": "ta_26_long_tactical_6", "text": "மருத்துவ நிவாரண வாகனங்கள் முகாம் ஒன்பதை அடைந்துள்ளன உணவு மற்றும் முதலுதவி பொருட்கள் விநியோகம் தொடங்கப்பட்டுள்ளது"},
        {"id": "ta_27_long_tactical_7", "text": "கோபுரம் மூன்றில் மின்சாரம் தடைபட்டுள்ளது என பாதுகாப்பு குழு தெரிவித்தது ஜெனரேட்டர்கள் இயக்கப்பட்டு தொடர்பு மீட்டெடுக்கப்பட்டது"},
        {"id": "ta_28_long_tactical_8", "text": "பிரிவு பிராவோ காட்டுப் பாதை வழியாக புள்ளி ஐந்துக்கு முன்னேறுகிறது எந்தவித சந்தேகத்திற்கிடமான நடவடிக்கையும் இல்லை"},
        {"id": "ta_29_long_tactical_9", "text": "ஆற்றுப் பாலம் கனரக வாகனங்களுக்கு பாதுகாப்பற்றது என பொறியியல் குழு தெரிவித்துள்ளது போக்குவரத்து கிழக்கு நோக்கி திருப்பப்பட்டுள்ளது"},
        {"id": "ta_30_long_tactical_10", "text": "அனைத்து சோதனைச் சாவடிகளும் பாதுகாப்பாக இருப்பதாக தலைமையகம் உறுதிப்படுத்தியுள்ளது இரவு ரோந்து குழுவினர் உடனடியாக பொறுப்பேற்கவும்"}
    ],
    "or": [
        {"id": "or_21_long_tactical_1", "text": "କନଭୟ ଆଲଫା ଏକ ବେସ ବ୍ରାଭୋକୁ ଜଣାଉଛି ଗ୍ରୀଡ ସମନ୍ୱୟ ବୟାଳିଶ ପାଖରେ ପଶ୍ଚିମ ବାଡ଼ ଭାଙ୍ଗିଯାଇଛି ତୁରନ୍ତ ଚିକିତ୍ସା ସାହାଯ୍ୟ ଆବଶ୍ୟକ"},
        {"id": "or_22_long_tactical_2", "text": "ଷ୍ଟେସନ ଡେଲଟା ନିଶ୍ଚିତ କରୁଛି ସେକ୍ଟର ତିନିରେ ଅମ୍ଳଜାନ ସ୍ତର ତିରିଶ ପ୍ରତିଶତକୁ ଖସିଆସିଛି ସମସ୍ତ କର୍ମଚାରୀ ଦକ୍ଷିଣ ଗେଟକୁ ଯାଆନ୍ତୁ"},
        {"id": "or_23_long_tactical_3", "text": "ସନ୍ଧାନ ଓ ଉଦ୍ଧାରକାରୀ ଦଳ ବାର ବନ୍ୟା ପ୍ରଭାବିତ ଅଞ୍ଚଳକୁ ଯାଉଛି ଜଳସ୍ତର ଦୁଇ ମିଟର ବୃଦ୍ଧି ପାଇଛି ଡଙ୍ଗା ମୁତୟନ କରାଯାଇଛି"},
        {"id": "or_24_long_tactical_4", "text": "ଜରୁରୀ ସତର୍କତା ଉପକୂଳ ସେକ୍ଟର ସାତରେ ଭୀଷଣ ଝଡ଼ ମାଡ଼ିଆସୁଛି ପବନର ବେଗ ସତୁରି କିଲୋମିଟର ସମସ୍ତେ ସୁରକ୍ଷିତ ରୁହନ୍ତୁ"},
        {"id": "or_25_long_tactical_5", "text": "କମାଣ୍ଡ ସେଣ୍ଟର ସମସ୍ତ ପାଟ୍ରୋଲିଂ ଦଳକୁ ପଶ୍ଚିମ ରାଜପଥ ଛକ ଚାରିକୁ ଯିବାକୁ ଏବଂ ରେଡିଓ ଯୋଗାଯୋଗ ରଖିବାକୁ ନିର୍ଦ୍ଦେଶ ଦେଉଛି"},
        {"id": "or_26_long_tactical_6", "text": "ଡାକ୍ତରୀ ସାହାଯ୍ୟ କନଭୟ ରିଲିଫ ଶିବିର ନଅରେ ପହଞ୍ଚିଛି ଖାଦ୍ୟ ଏବଂ ପ୍ରାଥମିକ ଚିକିତ୍ସା ସାମଗ୍ରୀ ବଣ୍ଟନ ଆରମ୍ଭ ହୋଇଛି"},
        {"id": "or_27_long_tactical_7", "text": "ରିଲେ ଟାୱାର ତିନିରେ ବିଦ୍ୟୁତ ସରବରାହ ବନ୍ଦ ଥିବା ସୂଚନା ମିଳିଛି ଜେନେରେଟର ଚାଲୁ କରି ଯୋଗାଯୋଗ ପୁନଃସ୍ଥାପିତ ହୋଇଛି"},
        {"id": "or_28_long_tactical_8", "text": "ଦଳ ବ୍ରାଭୋ ଜଙ୍ଗଲ ରାସ୍ତା ଦେଇ ପଏଣ୍ଟ ପାଞ୍ଚ ଆଡ଼କୁ ବଢ଼ୁଛି କୌଣସି ଅସୁବିଧା ନାହିଁ ସବୁ ସୁରକ୍ଷିତ ଅଛି"},
        {"id": "or_29_long_tactical_9", "text": "ଇଞ୍ଜିନିୟରିଂ ଦଳ ନଦୀ ପୋଲକୁ ଭାରୀ ଯାନ ପାଇଁ ବିପଜ୍ଜନକ ଘୋଷଣା କରିଛି ସମସ୍ତ ଯାତାୟାତ ପୂର୍ବ ଦିଗକୁ ବଦଳାଯାଇଛି"},
        {"id": "or_30_long_tactical_10", "text": "ମୁଖ୍ୟ କାର୍ଯ୍ୟାଳୟ ନିଶ୍ଚିତ କରୁଛି ସବୁ ମୁଖ୍ୟ ଫାଟକ ସୁରକ୍ଷିତ ଅଛି ରାତ୍ରୀ ପାଟ୍ରୋଲିଂ ଦଳ ତୁରନ୍ତ ଦାୟିତ୍ୱ ଗ୍ରହଣ କରନ୍ତୁ"}
    ]
}

def add_noise(signal: np.ndarray, snr_db: float = 15.0) -> np.ndarray:
    p_signal = np.mean(signal ** 2)
    if p_signal <= 1e-9:
        return signal
    p_noise = p_signal / (10 ** (snr_db / 10.0))
    # Gaussian noise with band-limited shaping
    noise = np.random.normal(0, np.sqrt(p_noise), len(signal))
    noisy = signal + noise
    # Prevent hard clipping
    max_val = np.max(np.abs(noisy))
    if max_val > 0.98:
        noisy = noisy * (0.98 / max_val)
    return noisy.astype(np.float32)

def init_tts_engine(lang_code: str):
    cfg = VOICE_CONFIGS[lang_code]
    model_dir = cfg["dir"]
    is_piper = cfg["is_piper"]

    model_files = list(model_dir.glob("*.onnx"))
    if not model_files:
        raise FileNotFoundError(f"No onnx model in {model_dir}")
    model_file = model_files[0]
    tokens_file = model_dir / "tokens.txt"

    data_dir = str(APP_ASSETS_DIR / "tts-en-amy" / "espeak-ng-data") if is_piper else ""

    tts_config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            vits=sherpa_onnx.OfflineTtsVitsModelConfig(
                model=str(model_file),
                tokens=str(tokens_file),
                data_dir=data_dir,
                noise_scale=0.667,
                noise_scale_w=0.8,
                length_scale=1.0
            ),
            num_threads=2,
            debug=False,
            provider="cpu"
        )
    )
    return sherpa_onnx.OfflineTts(tts_config)

def main():
    print("=" * 80)
    print("STEP 1: GENERATING 30-UTTERANCE STANDARDIZED CORPUS (CLEAN + 15dB NOISY)")
    print("=" * 80)

    CLEAN_DIR.mkdir(parents=True, exist_ok=True)
    NOISY_DIR.mkdir(parents=True, exist_ok=True)

    with open(EXISTING_DATASET, "r", encoding="utf-8") as f:
        existing_data = json.load(f)

    unified_corpus = {}

    for code, extra_samples in LONG_UTTERANCES.items():
        lang_info = existing_data[code]
        lang_name = lang_info["language"]
        base_samples = lang_info["samples"] # 20 samples

        print(f"\nProcessing {lang_name} ({code}): 20 existing + 10 long = 30 utterances...")

        # 1. Copy or verify existing 20 clean audio files
        all_samples = []
        for sample in base_samples:
            sid = sample["id"]
            text = sample["text"]
            cat = sample["category"]
            clean_wav = CLEAN_DIR / f"{sid}.wav"
            src_wav = EXISTING_AUDIO_DIR / f"{sid}.wav"

            if src_wav.exists() and not clean_wav.exists():
                shutil.copy(src_wav, clean_wav)

            all_samples.append({
                "id": sid,
                "category": cat,
                "text": text,
                "group": "short" if "short" in sid else ("long" if "long" in sid else "medium")
            })

        # 2. Synthesize new 10 long utterances
        tts_engine = None
        for sample in extra_samples:
            sid = sample["id"]
            text = sample["text"]
            clean_wav = CLEAN_DIR / f"{sid}.wav"

            if not clean_wav.exists():
                if tts_engine is None:
                    print(f"  Initializing TTS engine for {lang_name}...")
                    tts_engine = init_tts_engine(code)
                print(f"  Synthesizing {sid}...")
                audio = tts_engine.generate(text, sid=0)
                samples_arr = np.array(audio.samples, dtype=np.float32)
                # Resample to 16kHz if needed
                if audio.sample_rate != 16000:
                    import scipy.signal
                    num_samples = int(len(samples_arr) * 16000 / audio.sample_rate)
                    samples_arr = scipy.signal.resample(samples_arr, num_samples).astype(np.float32)
                sf.write(clean_wav, samples_arr, 16000, subtype="PCM_16")

            all_samples.append({
                "id": sid,
                "category": "long_tactical",
                "text": text,
                "group": "long"
            })

        # 3. Generate 15 dB SNR noisy versions for all 30 utterances
        for sample in all_samples:
            sid = sample["id"]
            clean_wav = CLEAN_DIR / f"{sid}.wav"
            noisy_wav = NOISY_DIR / f"{sid}.wav"

            if clean_wav.exists() and not noisy_wav.exists():
                data, sr = sf.read(clean_wav, dtype="float32")
                noisy_data = add_noise(data, snr_db=15.0)
                sf.write(noisy_wav, noisy_data, sr, subtype="PCM_16")

        unified_corpus[code] = {
            "language": lang_name,
            "samples": all_samples
        }

    # Write unified dataset JSON
    with open(OUTPUT_JSON, "w", encoding="utf-8") as f:
        json.dump(unified_corpus, f, ensure_ascii=False, indent=2)

    total_clean = len(list(CLEAN_DIR.glob("*.wav")))
    total_noisy = len(list(NOISY_DIR.glob("*.wav")))
    print(f"\nCorpus generation complete!")
    print(f"Unified JSON: {OUTPUT_JSON}")
    print(f"Clean WAVs: {total_clean} files in {CLEAN_DIR}")
    print(f"Noisy WAVs: {total_noisy} files in {NOISY_DIR}")

if __name__ == "__main__":
    main()
