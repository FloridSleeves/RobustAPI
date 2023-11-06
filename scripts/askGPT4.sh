cd ..

python askGPT.py \
--model_name gpt-4-0613 \
--api_keys_file keys.txt \
--result_dir ./results/gpt4/ \
--shot_number 1 \
--temperature 0 \
--top_p 1.0

