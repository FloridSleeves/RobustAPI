cd ..

python askGPT.py \
--model_name gpt-3.5-turbo-0613 \
--api_keys_file keys.txt \
--result_dir ./results/gpt3.5/retrieve_rule/ \
--shot_number 1 \
--temperature 0 \
--top_p 1.0 \
--shot_type rule
